# Cloud Run Deployment

This document covers the **one-time GCP bootstrap** required before
`.github/workflows/deploy-cloudrun.yml` can deploy the `web-app` container
to Google Cloud Run.

The workflow itself is a manual `workflow_dispatch` trigger: pick an image
tag (e.g. `v1.0.0-rc1`, `latest`, `edge`) that the release pipeline already
pushed to `ghcr.io/karlkauc/findatex-validator-web`. The deploy job mirrors
that image into Artifact Registry in the target region (region-local pull
= faster cold start) and rolls out the Cloud Run service.

Authentication uses **Workload Identity Federation** (OIDC) — no JSON keys
are stored in the repo or in GitHub Secrets.

## Target configuration

| Setting               | Value                              |
| --------------------- | ---------------------------------- |
| Region                | `europe-west3` (Frankfurt)         |
| Service name          | `findatex-validator-web`           |
| Auth                  | `--allow-unauthenticated` (public) |
| Memory / CPU          | 2 GiB / 1 vCPU + CPU-boost         |
| Concurrency           | 80 per instance                    |
| Min / max instances   | 0 / 1                              |
| Request timeout       | 300 s                              |
| External validation   | off (`FINDATEX_WEB_EXTERNAL_ENABLED=false`) |

`max-instances=1` is intentional: `Bucket4j` (rate limit) and `ReportStore`
(post-validate XLSX cache) live in JVM memory per instance. Multiple
instances would split state and corrupt both the rate-limit and the
download flow. For the expected traffic of a validator app this is fine;
revisit only if usage exceeds what one 1-vCPU container can serve.

## One-time bootstrap

Run on a workstation with `gcloud` installed and `gcloud auth login` already done.
Adjust `PROJECT` if you want a different project id.

```bash
PROJECT=findatex-validator           # GCP project id (must be globally unique)
REPO=karlkauc/findatex-validator     # GitHub <owner>/<repo>
REGION=europe-west3

# 1) Project + APIs
gcloud projects create "$PROJECT" --name="FinDatEx Validator"   # skip if it already exists
gcloud config set project "$PROJECT"
gcloud services enable run.googleapis.com \
                       artifactregistry.googleapis.com \
                       iamcredentials.googleapis.com \
                       sts.googleapis.com

# 2) Artifact Registry repo (region-local image storage)
gcloud artifacts repositories create findatex \
  --location="$REGION" --repository-format=docker \
  --description="FinDatEx container images"

# 3) Service account that GitHub Actions will impersonate
gcloud iam service-accounts create github-deploy \
  --display-name="GitHub Actions deployer"
SA_EMAIL="github-deploy@$PROJECT.iam.gserviceaccount.com"

# 4) Minimal IAM roles for the SA
for role in roles/run.admin \
            roles/iam.serviceAccountUser \
            roles/artifactregistry.writer; do
  gcloud projects add-iam-policy-binding "$PROJECT" \
    --member="serviceAccount:$SA_EMAIL" --role="$role"
done

# 5) Workload Identity Federation pool + provider for GitHub OIDC
gcloud iam workload-identity-pools create github \
  --location=global --display-name="GitHub Actions"

PROJECT_NUM=$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')

gcloud iam workload-identity-pools providers create-oidc findatex \
  --location=global --workload-identity-pool=github \
  --display-name="FinDatEx repo" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" \
  --attribute-condition="assertion.repository == '$REPO'"

# 6) Allow this specific GitHub repo to impersonate the SA
gcloud iam service-accounts add-iam-policy-binding "$SA_EMAIL" \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$PROJECT_NUM/locations/global/workloadIdentityPools/github/attribute.repository/$REPO"

# 7) Print the values to register as GitHub repo Variables
echo "----- GitHub repo Variables -----"
echo "GCP_PROJECT=$PROJECT"
echo "GCP_DEPLOY_SA=$SA_EMAIL"
echo "GCP_WIF_PROVIDER=projects/$PROJECT_NUM/locations/global/workloadIdentityPools/github/providers/findatex"
```

## Register GitHub repo Variables

In the GitHub UI: **Settings → Secrets and variables → Actions → Variables tab**
(not the Secrets tab — these aren't secrets, just configuration):

| Variable           | Value (from script output)                                         |
| ------------------ | ------------------------------------------------------------------ |
| `GCP_PROJECT`      | the project id, e.g. `findatex-validator`                          |
| `GCP_DEPLOY_SA`    | `github-deploy@<project>.iam.gserviceaccount.com`                  |
| `GCP_WIF_PROVIDER` | `projects/<project-num>/locations/global/workloadIdentityPools/github/providers/findatex` |

## Running the deploy

1. Make sure the release pipeline has pushed the image tag you want. Tags
   pushed by `release.yml` (e.g. `v1.0.0-rc1`) appear at
   `ghcr.io/karlkauc/findatex-validator-web:<tag>`. The `latest` tag points
   at the most recent stable release; `edge` tracks `main`.
2. GitHub UI → **Actions → Deploy to Cloud Run → Run workflow**.
3. Enter the tag (e.g. `v1.0.0-rc1`).
4. The workflow logs end with the live service URL; it is also written to
   the run summary.

## Enabling external validation later

External validation (GLEIF + OpenFIGI) is off by default in the deployed
service. To turn it on without baking secrets into the image:

```bash
PROJECT=findatex-validator
REGION=europe-west3
SERVICE=findatex-validator-web

# Store the OpenFIGI key in Secret Manager (one-time)
echo -n "<your-openfigi-api-key>" | \
  gcloud secrets create findatex-openfigi-key \
    --replication-policy=automatic --data-file=-

# Grant the Cloud Run runtime SA access (default Cloud Run SA, or a custom one)
RUNTIME_SA=$(gcloud run services describe "$SERVICE" --region="$REGION" \
  --format='value(spec.template.spec.serviceAccountName)')
gcloud secrets add-iam-policy-binding findatex-openfigi-key \
  --member="serviceAccount:$RUNTIME_SA" \
  --role=roles/secretmanager.secretAccessor

# Update the service: turn the feature on, mount the secret as env
gcloud run services update "$SERVICE" --region="$REGION" \
  --update-env-vars=FINDATEX_WEB_EXTERNAL_ENABLED=true \
  --update-secrets=FINDATEX_WEB_EXTERNAL_OPENFIGI_KEY=findatex-openfigi-key:latest
```

GLEIF needs no key. Add proxy env vars (`FINDATEX_WEB_EXTERNAL_PROXY_*`)
the same way if egress requires a corporate proxy — but Cloud Run normally
has direct internet egress, so usually unnecessary.

## Custom domain

By default the service is reachable at the auto-generated
`https://findatex-validator-web-<hash>-ew.a.run.app`. Below is how to put
it behind a domain you own (e.g. `validator.example.com`) using **Cloud
Run domain mappings** — the simplest path: one CNAME, no static IP, no
load balancer, $0 added cost, managed TLS cert auto-issued and renewed.

### Picking a registrar

Recommended (no markup, modern DNS UI, EU-friendly):

- **Cloudflare Registrar** — at-cost pricing, sells `.com`, `.de`, `.app`,
  `.io`, etc. Default choice.
- **Porkbun** — cheap, free WHOIS privacy.
- **INWX** / **all-inkl** — German DENIC members; pick one of these if you
  want a `.de` from a German provider with German-language support.

Avoid GoDaddy, IONOS / 1&1: markup pricing and clunky DNS interfaces.

### Subdomain vs apex

| | Subdomain (`validator.example.com`) | Apex (`example.com`)             |
| ----------- | ---------------------------------- | -------------------------------- |
| DNS records | 1× CNAME                           | 4× A + 4× AAAA (no CNAME at apex by RFC) |
| Recommended | **yes — simpler, more flexible**   | only if the app *is* the brand site |

A subdomain frees the apex for marketing/redirects later. The rest of this
document assumes a subdomain; the apex variant is in a sub-section below.

### Step 1 — Verify domain ownership in Google Search Console

This is required once per **registrable domain** (i.e. `example.com`
covers all subdomains).

1. Open <https://search.google.com/search-console>.
2. Add property → choose the **"Domain"** variant (not "URL prefix") →
   enter `example.com`.
3. Search Console shows a TXT record. Add it at your registrar:

   | Field | Value                                                  |
   | ----- | ------------------------------------------------------ |
   | Type  | `TXT`                                                  |
   | Name / Host | `@` (apex; some panels show this as empty or `example.com`) |
   | Value | `google-site-verification=<token from Search Console>` |
   | TTL   | `300` (5 minutes — keep low until everything works)    |

4. Wait a minute, click **Verify** in Search Console. Confirm via gcloud:

   ```bash
   gcloud domains list-user-verified
   ```

   `example.com` should appear in the list.

### Step 2 — Create the Cloud Run domain mapping

```bash
DOMAIN=validator.example.com
PROJECT=findatex-validator
REGION=europe-west3
SERVICE=findatex-validator-web

gcloud beta run domain-mappings create \
  --service="$SERVICE" --domain="$DOMAIN" \
  --region="$REGION" --project="$PROJECT"
```

If the command errors with *"region not supported"*, see
[Alternative: HTTPS Load Balancer](#alternative-https-load-balancer) below.
At time of writing `europe-west3` is supported.

### Step 3 — Enter the DNS records at your registrar

For a **subdomain** like `validator.example.com` you need exactly one
record:

| Field | Value                  |
| ----- | ---------------------- |
| Type  | `CNAME`                |
| Name / Host | `validator` (only the leftmost label — the registrar will append the apex) |
| Target / Value | `ghs.googlehosted.com.` (note the trailing dot) |
| TTL   | `300` (raise to `3600` after everything works)  |

`gcloud beta run domain-mappings describe` prints the exact record to
add — the value above is the canonical Google target it returns:

```bash
gcloud beta run domain-mappings describe \
  --domain="$DOMAIN" --region="$REGION" --project="$PROJECT" \
  --format='value(status.resourceRecords)'
```

### Step 4 — Wait for the managed TLS certificate

Google issues a managed certificate as soon as the DNS resolves. Usually
5 – 15 minutes, sometimes up to an hour. Status:

```bash
gcloud beta run domain-mappings describe \
  --domain="$DOMAIN" --region="$REGION" --project="$PROJECT" \
  --format='value(status.conditions)'
```

When `CertificateProvisioned=True` appears, browse to
`https://validator.example.com` — the validator should answer.

### Apex domain variant

If you really want `example.com` (no subdomain) to point at the service,
the registrar must let you set A/AAAA records at the apex (most do; some
basic ones only support CNAME and won't work). Replace the CNAME step
with:

| Field | Values                                                                                          |
| ----- | ----------------------------------------------------------------------------------------------- |
| Type  | `A`                                                                                             |
| Name / Host | `@` (apex)                                                                                |
| Value | `216.239.32.21`, `216.239.34.21`, `216.239.36.21`, `216.239.38.21` — **four separate A records** |
| TTL   | `300`                                                                                           |

| Field | Values                                                                                                          |
| ----- | --------------------------------------------------------------------------------------------------------------- |
| Type  | `AAAA`                                                                                                          |
| Name / Host | `@` (apex)                                                                                                |
| Value | `2001:4860:4802:32::15`, `2001:4860:4802:34::15`, `2001:4860:4802:36::15`, `2001:4860:4802:38::15` — **four separate AAAA records** |
| TTL   | `300`                                                                                                           |

Verify the exact list via the same `gcloud … describe` call as above —
Google sometimes rotates the IPs.

### Optional: redirect www → apex (or apex → subdomain)

Most registrars (Cloudflare, Porkbun, INWX) offer a "URL forwarding" or
"page rule" feature. Two common setups:

- **App on `validator.example.com`, marketing on `example.com`**:
  no extra DNS needed beyond the subdomain mapping.
- **App on `example.com`, redirect `www.example.com` → `example.com`**:
  add a registrar-side 301 redirect from `www.example.com` to
  `https://example.com` (or set up a second domain mapping for `www`).

### Alternative: HTTPS Load Balancer

If domain mappings are not available in your region, or you want a static
IP, Cloud CDN, or Cloud Armor (WAF), set up a Global External HTTPS Load
Balancer with a Serverless NEG pointing at the Cloud Run service. Adds
~$18/month base cost and ~10 gcloud commands; consider only if domain
mapping is not an option.

## Tearing it down

```bash
gcloud beta run domain-mappings delete \
  --domain=validator.example.com --region=europe-west3   # if a domain was mapped
gcloud run services delete findatex-validator-web --region=europe-west3
gcloud artifacts repositories delete findatex --location=europe-west3
# (optional) drop the WIF setup and SA if you're done with the project entirely
```
