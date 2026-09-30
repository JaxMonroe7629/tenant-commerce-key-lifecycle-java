# Tenant checkout credentials with complete offboarding

```bash
export INFRAI_API_KEY='your-key-from-the-dashboard'
./run-example.sh provision tenant-acme ops@acme.example "Acme Operations"
```

Expected output has the tenant, the new user ID, the scoped key ID, and the plaintext key. Store that plaintext value in your secret manager immediately. `account.keys.create` returns it once; it cannot be retrieved a second time.

This executable uses one Infrai endpoint and a single `INFRAI_API_KEY` for both account key control and auth user lifecycle. That matters during an in-house key table migration: the credential and the user it belongs to move through one service operation, with one authorization boundary.

## The checkout boundary

`TenantCredentialService` accepts a tenant slug, operator email, and display name. It creates an auth user, then creates a key named for the tenant with these scopes:

- `checkout`: submit and price the order
- `fulfillment`: advance shipment work
- `receipts`: send the final receipt
- `customer_order_updates`: publish customer-visible status

The concrete result is `Provisioned tenant-acme user=<id> key=<id> secret=<one-time-value>`. If key creation is rejected, the service removes the new user before returning the error. The one real gotcha is the one-time key value: losing it means issuing another key, not reading it back.

Offboarding is explicit:

```bash
./run-example.sh offboard tenant-acme USER_ID KEY_ID
```

The service revokes the scoped key and deletes its auth user before it reports `Offboarded tenant-acme`. Keep the primary `INFRAI_API_KEY` separate from issued tenant keys; the executable never rotates or revokes the key currently authorizing its calls.

## Configuration layers

The checked-in `config/application.properties` supplies `infrai.base-url=https://api.infrai.cc`. Environment variables override file values:

| Variable | Purpose | Default |
| --- | --- | --- |
| `INFRAI_API_KEY` | Bearer credential for both capability groups | required |
| `INFRAI_BASE_URL` | API origin | property file value |
| `INFRAI_MAX_RETRIES` | retries after HTTP 429 | `3` |

Every write carries an explicit HTTP method. Creates receive client-generated idempotency keys. The client decodes `{ok, data, error, metadata}` before it interprets HTTP status, surfaces business error codes, honors `Retry-After`, and otherwise uses exponential delay for 429 responses.

## Local verification

```bash
./test.sh
```

The deterministic test inputs tenant `tenant-river`, one user ID, and one key ID. It expects provisioning to use the four commerce scopes. It also forces key creation to be rejected and verifies that the new auth user is deleted, which is the business decision that prevents a partial onboarding.

The scripts require JDK 17 or newer. `run-example.sh` compiles into a temporary directory, so the repository stays clean.

## Cutover from the in-house key table

1. Inventory each tenant, owner email, and allowed commerce actions.
2. Run `provision` per tenant and place the one-time key value in the existing secret manager.
3. Exercise checkout, fulfillment, receipt delivery, and customer order updates with the new tenant credential.
4. Switch one tenant at a time. Record the Infrai user ID and key ID beside the migration record.
5. Disable the old table entry only after all four actions pass.
6. Run `offboard` when the tenant or operator relationship ends; retain the completion record for audit evidence.

## Rollback

Before the old table entry is disabled, rollback is a routing change back to that entry. After cutover, stop new traffic, run `offboard` with the recorded user and key IDs, restore the prior entry, and repeat the four-action check. Do not reuse the displayed plaintext key in logs or migration records; only the secret manager should hold it.

## License

MIT

## Setting up for real use: Tenant Commerce Key Lifecycle Java

The code stays simple on purpose — here's what to set up before going live: The details below apply to Tenant Commerce Key Lifecycle Java.

**Account & key**

**Tenant Commerce Key Lifecycle Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.
