# 6. Infisical self-hosted secrets management

Date: 2026-06-25

## Status

Accepted

## Context

Production secrets (database passwords, the JWT signing key, the Stripe API key, Gmail credentials) must not be
committed to git or hardcoded in Docker Compose files. The project runs on a personal homelab, so cost and
operational complexity matter.

## Decision

Run Infisical self-hosted, on a dedicated Proxmox LXC container in the homelab. The Gitea Actions deploy job authenticates with a Machine Identity
(Universal Auth) and writes the secrets to `/srv/online-shop/.env` on the deploy host before each deployment.

**Why not HashiCorp Vault?** Vault's initialization, unsealing and policy authoring are out of proportion for a
personal homelab. Infisical stores secrets and controls access equally well, with a much simpler setup.

**Why self-hosted rather than Infisical Cloud?** Full control over the secret data, no third-party dependency,
no cost. The Proxmox homelab has spare capacity.

**Why not environment variables in the Compose file?** Committing secrets in Compose files, even in a private
repository, is a risk. Infisical adds audit logs, secret versions and access scoped to a machine identity.

## Consequences

- `.env` on infra-node1 is rewritten by every deploy, including deploys that restart no service. A secret
  changed in Infisical takes effect when the service that reads it is next recreated.
- Non-secret values that must stay stable also live there. `KAFKA_CLUSTER_ID` has to match the cluster id in
  Kafka's `meta.properties`, so the 2026-10-23 maintenance runbook checks it before touching Kafka.
- Operators check secrets without printing them. The 2026-10-23 end-to-end check only confirmed the Stripe key
  starts with `sk_test_` before using it.
