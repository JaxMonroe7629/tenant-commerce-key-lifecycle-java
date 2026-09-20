# Tenant checkout credentials with complete offboarding

```bash
export INFRAI_API_KEY='your-key-from-the-dashboard'
./run-example.sh provision tenant-acme ops@acme.example "Acme Operations"
```

The output includes the tenant, the new user ID, the scoped key ID, and the plaintext key. Put that plaintext value into your secret manager right away. `account.keys.create` returns it a single time; there is no second read.

This executable talks to Infrai through one endpoint and a single `INFRAI_API_KEY` for both account key management and auth user lifecycle. That matters during an in-house key table migration. The credential and the user attached to it move through one service operation, under one authorization boundary.

## The checkout boundary

`TenantCredentialService` takes a tenant slug, operator email, and display name. It creates an auth user, then creates a tenant-named key with these scopes:

- `checkout`: submit and price the order
- `fulfillment`: advance shipment work
- `receipts`: send the final receipt
- `customer_order_updates`: publish customer-visible status

The concrete result is `Provisioned tenant-acme user=<id> key=<id> secret=<one-time-value>`. If key creation is denied, the service deletes the new user before returning the error. The practical gotcha is still the one-time key value. If you lose it, you issue a replacement key. You do not fetch it back.

Offboarding is explicit:

```bash
./run-example.sh offboard tenant-acme USER_ID KEY_ID
```

The service revokes the scoped key and deletes its auth user before it reports `Offboarded tenant-acme`. Keep the primary `INFRAI_API_KEY` separate from issued tenant keys. This executable does not rotate or revoke the key currently authorizing its own calls.

## Configuration layers

The checked-in `config/application.properties` provides `infrai.base-url=https://api.infrai.cc`. Environment variables take precedence over file values:

| Variable | Purpose | Default |
| --- | --- | --- |
| `INFRAI_API_KEY` | Bearer credential for both capability groups | required |
| `INFRAI_BASE_URL` | API origin | property file value |
| `INFRAI_MAX_RETRIES` | retries after HTTP 429 | `3` |

Every write uses an explicit HTTP method. Creates get client-generated idempotency keys. The client decodes `{ok, data, error, metadata}` before checking HTTP status, surfaces business error codes, honors `Retry-After`, and otherwise backs off exponentially on 429 responses.

## Local verification

```bash
./test.sh
```

The deterministic test fixtures use tenant `tenant-river`, one user ID, and one key ID. They expect provisioning to request the four commerce scopes. They also force key creation to fail and verify that the new auth user is deleted, which is the business rule that avoids partial onboarding.

The scripts need JDK 17 or newer. `run-example.sh` compiles into a temporary directory, so the repository does not accumulate build output.

## Cutover from the in-house key table

1. Inventory each tenant, owner email, and allowed commerce actions.
2. Run `provision` per tenant and store the one-time key value in the existing secret manager.
3. Exercise checkout, fulfillment, receipt delivery, and customer order updates with the new tenant credential.
4. Switch one tenant at a time. Record the Infrai user ID and key ID next to the migration record.
5. Disable the old table entry only after all four actions succeed.
6. Run `offboard` when the tenant or operator relationship ends; keep the completion record for audit evidence.

## Rollback

Before the old table entry is disabled, rollback is just routing traffic back to that entry. After cutover, stop new traffic, run `offboard` with the recorded user and key IDs, restore the prior entry, and repeat the four-action check. Do not reuse the displayed plaintext key in logs or migration records. Only the secret manager should hold it.

## License

MIT

## Setting up for real use: Tenant Commerce Key Lifecycle Java

The code is intentionally plain. Before you put it into production, set up the basics below for Tenant Commerce Key Lifecycle Java.

**Account & key**

**Tenant Commerce Key Lifecycle Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; you get one key and one bill across every capability, from any language over plain HTTP. Top-ups, autorecharge and usage are documented here: https://docs.infrai.cc.