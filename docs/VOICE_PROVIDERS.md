# Voice providers, model choice and cost

Reviewed 2026-10-25. Prices are limited to the OpenAI models selectable in this application; this is not a measured quality ranking.

## What works in the published application

**The author's physical-device tests used the OpenAI API on a Pixel 8 Pro only. No other provider or phone model has been validated.** Provider-level testing does not establish that every selectable model and every call scenario was tested.

The source already offers two OpenAI choices in `RealtimeModel`: `gpt-realtime-2.1` (Complet, default) and `gpt-realtime-2.1-mini` (Economique). `DirectOpenAi` and `PhoneAgent` use OpenAI endpoints and an OpenAI-only host restriction. A different provider cannot be enabled by pasting its key into the existing screen. Keep that restriction until a reviewed provider-specific adapter exists.

The existing choices select a model, not a separately installed agent. The app supplies the mission, tools and audio relay. This guide does not change the APK or enable new providers.

## OpenAI prices, lowest to highest

Only the two OpenAI Realtime models selectable in the application are priced here. Rates reviewed on 2026-10-25, in USD before taxes, per million tokens unless noted.

| Model / app label | Audio input | Audio output | Text input | Text output | Example: 30 s received speech + 30 s generated speech |
|---|---:|---:|---:|---:|---:|
| gpt-realtime-2.1-mini / Economique | $10 | $20 | $0.60 | $2.40 | $0.0150 |
| gpt-realtime-2.1 / Complet | $32 | $64 | $4 | $24 | $0.0480 |

The example counts **new audio only**, using 600 input and 1,200 output audio tokens per minute: `0.5 * (600 * input_rate + 1200 * output_rate) / 1,000,000`. It is not the price of a complete call. Text, conversation history, tools, carrier/data charges and taxes are additional where applicable. Silence, interruptions and speech proportions also affect usage. A lower price does not establish equal voice quality or mission success.

Cached input prices per million tokens: Mini audio $0.30 and text $0.06; Complet audio $0.40 and text $0.40. Cache eligibility and actual usage determine the invoice. [Official OpenAI pricing](https://developers.openai.com/api/docs/pricing) · [Audio accounting](https://developers.openai.com/api/docs/guides/voice-latency-cost?voice-api=realtime).

## Other providers

Other voice providers are not integrated or tested. Their prices are deliberately not displayed here. Request an integration using the dedicated issue template below; feasibility and validation come before any support claim.

## Contribution scope: make provider choice real

1. Define a provider interface for connect, audio send/receive, interruption, tool calls, usage, errors and close. Preserve the current OpenAI behavior as the baseline.
2. Keep credentials isolated by provider, validate destination hosts, and never automatically send a call to a fallback provider. Show the selected recipient of audio before starting.
3. Adapt sample rates, event formats, authentication and cancellation for one new provider at a time. Never embed account keys or weaken relay authorization.
4. Use mocked streams to test interruption, disconnect, stale output rejection, hangup, manual takeover and usage accounting before real calls.
5. Evaluate on consenting calls with the same mission, language, speech split and context length. Report success, latency, interruptions, actual billed cost, phone/app/model versions and failures. Test French and Darija separately if claimed.
6. Keep experimental adapters unavailable by default until documented device evidence exists. Preserve original released APKs; publish a separate prerelease for a new implementation.

## Français

Les essais actuels ont été faits avec l’API OpenAI sur le Pixel 8 Pro de l’auteur. Seuls les tarifs des deux modèles proposés sont affichés, du moins cher au plus cher : **Économique (Mini)** puis **Complet**. L’exemple de 0,0150 USD pour Mini et 0,0480 USD pour Complet couvre uniquement 30 secondes d’audio reçu et 30 secondes d’audio généré, pas la facture complète d’un appel. Les tarifs des autres fournisseurs ne sont pas affichés ; leur intégration peut être demandée ci-dessous.

### Request another voice provider / Demander un autre fournisseur vocal

Additional voice providers may be added in response to community requests, subject to technical feasibility, contributor availability and validation. [Open a voice-provider request](https://github.com/iasibadr/whatsapp-ai/issues/new?template=voice_provider_request.md) with the service name, public API documentation and your intended use. A request does not guarantee integration or a release date. Only OpenAI is supported today.

D’autres fournisseurs vocaux pourront être ajoutés à la demande de la communauté, sous réserve de faisabilité technique, de disponibilité des contributeurs et de tests. [Ouvrir une demande](https://github.com/iasibadr/whatsapp-ai/issues/new?template=voice_provider_request.md) en indiquant le service souhaité, sa documentation API publique et votre usage. L’ajout n’est pas garanti et aucun délai n’est promis. Actuellement, seule l’API OpenAI est prise en charge. Ne partagez aucune clé API ni donnée personnelle.
