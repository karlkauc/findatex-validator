package com.findatex.validator.web.api;

import com.findatex.validator.template.api.ProfileKey;
import com.findatex.validator.template.api.TemplateDefinition;
import com.findatex.validator.template.api.TemplateRegistry;
import com.findatex.validator.template.api.TemplateVersion;
import com.findatex.validator.web.config.WebConfig;
import com.findatex.validator.web.dto.TemplateInfo;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;

@Path("/api/templates")
@Produces(MediaType.APPLICATION_JSON)
public class TemplateResource {

    @Inject
    WebConfig config;

    @GET
    public List<TemplateInfo> list() {
        boolean operatorEnabled = config.external().enabled();
        List<TemplateInfo> result = new ArrayList<>();
        for (TemplateDefinition def : TemplateRegistry.all()) {
            List<TemplateInfo.VersionInfo> versions = new ArrayList<>();
            for (TemplateVersion v : def.versions()) {
                List<TemplateInfo.ProfileInfo> profiles = new ArrayList<>();
                for (ProfileKey p : def.profilesFor(v).all()) {
                    profiles.add(new TemplateInfo.ProfileInfo(p.code(), p.displayName()));
                }
                versions.add(new TemplateInfo.VersionInfo(
                        v.version(),
                        v.label(),
                        v.releaseDate() == null ? null : v.releaseDate().toString(),
                        profiles));
            }
            // External validation is gated by operator opt-in (FINDATEX_WEB_EXTERNAL_ENABLED)
            // and by the template declaring at least one ISIN/LEI column. We probe the latest
            // version: per-version drift would require expanding TemplateInfo, which has not
            // been needed yet.
            boolean externalAvailable = operatorEnabled
                    && !def.externalValidationConfigFor(def.latest()).isEmpty();
            result.add(new TemplateInfo(def.id().name(), def.displayName(), versions, externalAvailable));
        }
        return result;
    }
}
