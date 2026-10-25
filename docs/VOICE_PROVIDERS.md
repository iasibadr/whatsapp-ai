# Voice providers, model choice and cost

Reviewed 2026-10-25. This is a documented shortlist, not an exhaustive market survey or a measured quality ranking.

## What works in the published application

**The author's physical-device tests used the OpenAI API on a Pixel 8 Pro only. No other provider or phone model has been validated.** Provider-level testing does not establish that every selectable model and every call scenario was tested.

The source already offers two OpenAI choices in `RealtimeModel`: `gpt-realtime-2.1` (Complet, default) and `gpt-realtime-2.1-mini` (Economique). `DirectOpenAi` and `PhoneAgent` use OpenAI endpoints and an OpenAI-only host restriction. A different provider cannot be enabled by pasting its key into the existing screen. Keep that restriction until a reviewed provider-specific adapter exists.

The existing choices select a model, not a separately installed agent. The app supplies the mission, tools and audio relay. This guide does not change the APK or enable new providers.

## Indicative prices, lowest to highest for one declared scenario

Scenario: a fresh 60-second voice interaction with 30 seconds of received speech and 30 seconds of generated speech. USD, standard paid prices, before taxes. Token-based rows count only new audio; session-based rows count the full minute. This is an illustrative subtotal, **not the actual invoice or a proven cheapest-provider ranking**. Context, text, tools, reconnects and other charges can change the order.

| Order in this scenario | Provider / option | Illustrative USD subtotal | Application status |
|---|---|---:|---|
| 1 | Google Gemini 3.8 Live / 3.1 Flash Live Preview | about 0.0115 | Candidate; not integrated or tested |
| 2 | OpenAI gpt-realtime-2.1-mini | 0.0150 | Selectable in source; not separately certified by this guide |
| 3 | OpenAI gpt-realtime-2.1 | 0.0480 | Default in source; only OpenAI has device-test evidence |
| 4 | Deepgram Voice Agent Standard, pay-as-you-go | 0.0750 | Candidate; not integrated or tested |
| 5 | xAI Grok Voice 3.0 | 0.0800, plus text-input charges | Candidate; not integrated or tested |

OpenAI: audio input/output rates per million tokens are $10/$20 for Mini and $32/$64 for Complet. Using 600 input and 1,200 output audio tokens per minute gives `0.5 * (600 * input_rate + 1200 * output_rate) / 1,000,000`. Text rates and cached input differ. Reused conversational context can add cost. [Pricing](https://developers.openai.com/api/docs/pricing) · [Audio accounting](https://developers.openai.com/api/docs/guides/voice-latency-cost?voice-api=realtime).

Google: its published approximate audio rates are $0.005 per input minute and $0.018 per output minute, hence `0.5 * (0.005 + 0.018)`. This uses Google's rounded duration equivalents, not OpenAI's tokenizer. Live history can be rebilled; transcription and tools may add charges. Free-tier data conditions differ from the paid tier and are not used here. [Pricing](https://ai.google.dev/gemini-api/docs/pricing) · [Live billing](https://ai.google.dev/gemini-api/docs/live-api/best-practices).

Deepgram: Standard pay-as-you-go is $0.075/minute. BYO configurations have separate component costs and are not equivalent to this row. [Pricing](https://deepgram.com/pricing).

xAI: the current pricing page lists Grok Voice 3.0 at $0.08/minute plus $0.004 per text input. An overview still advertises a starting $0.05 rate; do not use that older headline for this model. Verify the exact account/model tariff before integrating. [Current pricing](https://docs.x.ai/developers/pricing).

Carrier calls/data, model text/history, tooling, device costs, optional backend hosting and maintenance are additional. None of these figures demonstrates a saving against Twilio or another complete telephony service.

## Anthropic and further candidates

- **Anthropic Claude:** consider a composed voice pipeline: speech recognition -> Claude -> speech synthesis. Anthropic's official cookbook demonstrates this with ElevenLabs. The total is STT + Claude input/output + TTS + any orchestration/hosting. There is no single comparable voice-minute price established here; it cannot honestly be inserted into the ranking without choosing all components. Not integrated or tested. [Official voice example](https://platform.claude.com/cookbook/third-party-elevenlabs-low-latency-stt-claude-tts).
- **Amazon Nova 2 Sonic:** a speech-to-speech candidate using Bedrock bidirectional streaming. Regional pricing, account access and a safe Android authentication design need evaluation before estimating a complete call. Not integrated or tested. [Model documentation](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-amazon-nova-2-sonic.html) · [Pricing](https://aws.amazon.com/nova/pricing/).
- **ElevenLabs:** possible speech synthesis/provider component, including a Claude pipeline. Select the plan and components before quoting a complete price. No compatibility claim for this APK.
- **OpenAI GPT-Live:** another architecture, listed at $0.05 per session minute plus backend model/tool costs; not a replacement model ID for the existing Realtime integration and not included in the audio-only ranking. [Pricing](https://developers.openai.com/api/docs/pricing).

## Contribution scope: make provider choice real

1. Define a provider interface for connect, audio send/receive, interruption, tool calls, usage, errors and close. Preserve the current OpenAI behavior as the baseline.
2. Keep credentials isolated by provider, validate destination hosts, and never automatically send a call to a fallback provider. Show the selected recipient of audio before starting.
3. Adapt sample rates, event formats, authentication and cancellation for one new provider at a time. Never embed account keys or weaken relay authorization.
4. Use mocked streams to test interruption, disconnect, stale output rejection, hangup, manual takeover and usage accounting before real calls.
5. Evaluate on consenting calls with the same mission, language, speech split and context length. Report success, latency, interruptions, actual billed cost, phone/app/model versions and failures. Test French and Darija separately if claimed.
6. Keep experimental adapters unavailable by default until documented device evidence exists. Preserve original released APKs; publish a separate prerelease for a new implementation.

## Francais

Les essais actuels ont ete faits avec l'API OpenAI sur le Pixel 8 Pro de l'auteur. Le code propose deja Complet et Economique chez OpenAI. Les autres fournisseurs sont des pistes de contribution, pas des options disponibles dans l'APK actuel. Le classement ci-dessus compare un scenario d'audio neuf ; la facture complete et la qualite doivent etre mesurees. Claude necessite ici une chaine reconnaissance vocale + modele + synthese vocale, avec le cout de chaque composant.

### Request another voice provider / Demander un autre fournisseur vocal

Additional voice providers may be added in response to community requests, subject to technical feasibility, contributor availability and validation. [Open a voice-provider request](https://github.com/iasibadr/whatsapp-ai/issues/new?template=voice_provider_request.md) with the service name, public API documentation and your intended use. A request does not guarantee integration or a release date. Only OpenAI is supported today.

D’autres fournisseurs vocaux pourront être ajoutés à la demande de la communauté, sous réserve de faisabilité technique, de disponibilité des contributeurs et de tests. [Ouvrir une demande](https://github.com/iasibadr/whatsapp-ai/issues/new?template=voice_provider_request.md) en indiquant le service souhaité, sa documentation API publique et votre usage. L’ajout n’est pas garanti et aucun délai n’est promis. Actuellement, seule l’API OpenAI est prise en charge. Ne partagez aucune clé API ni donnée personnelle.
