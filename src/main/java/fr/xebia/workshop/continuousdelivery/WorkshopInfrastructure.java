package fr.xebia.workshop.continuousdelivery;

public class WorkshopInfrastructure {

    private String githubAccount;
    private String githubUsername;
    private String githubPassword;
    private String nexusDomainName;
    private String nexusPublicIp;

    private WorkshopInfrastructure() {
    }

    public static Builder create() {
        return new Builder();
    }

    public String getGithubGuestAccountName() {
        return githubAccount;
    }

    public String getGithubGuestAccountPassword() {
        return githubPassword;
    }

    public String getGithubGuestAccountUrl() {
        return "https://github.com/" + githubAccount + "/";
    }

    public String getGithubGuestAccountUsername() {
        return githubUsername;
    }

    public String getNexusUrlWithDomainName() {
        return "http://" + nexusDomainName + ":8081/nexus/";
    }

    public String getNexusPublicIp() {
        return nexusPublicIp;
    }

    public String getNexusUrlWithIp() {
        return "http://" + nexusPublicIp + ":8081/nexus/";
    }

    public static class Builder {

        private WorkshopInfrastructure infra = new WorkshopInfrastructure();

        public WorkshopInfrastructure build() {
            return infra;
        }

        public Builder withGithubGuestInfo(String account, String username, String password) {
            infra.githubAccount = account;
            infra.githubUsername = username;
            infra.githubPassword = password;
            return this;
        }

        public Builder withNexusDomainName(String domainName) {
            infra.nexusDomainName = domainName;
            return this;
        }

        public Builder withNexusPublicIp(String ip) {
            infra.nexusPublicIp = ip;
            return this;
        }
    }
}
