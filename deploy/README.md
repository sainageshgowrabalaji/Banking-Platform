# Deployment

Stubs for phase 4. Nothing here has been applied to a cluster or a cloud account yet.

| Folder | What it will hold |
|---|---|
| `helm/bank-service` | One Helm chart shared by every service. Each service only supplies its own values |
| `terraform/aws` | The AWS environment as code. Network, EKS, RDS for PostgreSQL, MSK for Kafka |

The path to production is the one most banks use today.

1. A merge to `master` builds, tests and scans the code, then pushes one image per service
2. The pipeline updates the image tag in the environment's values file
3. Argo CD sees the change in git and rolls it out to Kubernetes
4. Readiness probes keep traffic away from a pod until it is ready, and a failed rollout is undone by reverting the commit
