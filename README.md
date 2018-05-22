# DeployEZ
Scala based service to deploy microservices via command line

This is designed to be extended to allow a new service to be created from scratch in a few steps.

This will also allow you to deploy from command line rather than having to use Jenkins

1. Create new repository/service
2. Create app configs
3. Add service manager configuration
4. Create Jenkins open/build config
5. Seed open Jenkins
6. Build the service on ci open
7. Deploy to dev
8. Update Jenkins config
9. Seed QA Jenkins
10. Seed Staging Jenkins
11. Deploy to QA and Staging
12. Create Grafana Dashboard
13. Create Kibana Dashboard