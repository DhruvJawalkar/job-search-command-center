package dev.dhruv.jobsearch.skill;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
class OpportunityCohortClassifier {

    private static final Pattern AI = Pattern.compile("\\b(ai|ml|machine learning|artificial intelligence|genai|agentic|agent)\\b");
    private static final Pattern SECURITY = Pattern.compile("\\b(security|identity|iam|privacy|trust|authentication|authorization)\\b");
    private static final Pattern DATA = Pattern.compile("\\b(data|analytics|streaming|kafka|database|lakehouse|warehouse)\\b");
    private static final Pattern CLOUD = Pattern.compile("\\b(cloud|infrastructure|platform|sre|reliability|observability|devops|production engineering)\\b");
    private static final Pattern BACKEND = Pattern.compile("\\b(backend|distributed|java|microservices|server)\\b");

    RoleFamily roleFamily(String roleTitle) {
        String title = normalized(roleTitle);
        if (AI.matcher(title).find()) return RoleFamily.AI_ML_PLATFORM;
        if (SECURITY.matcher(title).find()) return RoleFamily.SECURITY_IDENTITY;
        if (DATA.matcher(title).find()) return RoleFamily.DATA_PLATFORM;
        if (CLOUD.matcher(title).find()) return RoleFamily.CLOUD_INFRASTRUCTURE;
        if (BACKEND.matcher(title).find()) return RoleFamily.BACKEND_DISTRIBUTED_SYSTEMS;
        return RoleFamily.GENERAL_SOFTWARE;
    }

    SeniorityBand seniority(String roleTitle) {
        String title = normalized(roleTitle);
        if (Pattern.compile("\\b(principal|distinguished|fellow)\\b").matcher(title).find()) {
            return SeniorityBand.PRINCIPAL_OR_ABOVE;
        }
        if (Pattern.compile("\\bstaff\\b").matcher(title).find()) return SeniorityBand.STAFF;
        if (Pattern.compile("\\b(lead|architect)\\b").matcher(title).find()) return SeniorityBand.LEAD_OR_ARCHITECT;
        if (Pattern.compile("\\b(senior|sr)\\b").matcher(title).find()) return SeniorityBand.SENIOR;
        return SeniorityBand.OTHER_OR_UNSPECIFIED;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('-', ' ');
    }
}
