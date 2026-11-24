# Echo-inspired local memory, phase 1

Reference: [Daedalus Echo](https://github.com/DaedalusApps/daedalus-echo), MIT license. Reviewed LocalLlmService, EmbeddingService and GraphRetrieval. This change implements original Gün İzi code inspired by lazy resource loading and topic expansion; it does not import Echo code or its models.

- Existing checkpoint reuse now avoids loading llama.cpp if every required generation is already cached. No persistent native pointer across jobs; no change to model, grammar, checkpoints or evidence rows.
- Work Memory can search all saved reports locally, returning ranked Turkish-normalized lexical hits and exact shared-topic rows from other reports, linked to source audio. This is NOT vector/semantic embedding search or generated answers.
- GPU is not enabled here: pinned CallVault JNI explicitly sets n_gpu_layers=0. MediaPipe .task models cannot be substituted for existing GGUF files. Evaluate a Vulkan backend with CPU fallback, model/license compatibility and device benchmarks on a separate branch before enabling. Embedding search needs a multilingual encoder and a versioned, private local index.
- Existing GPL-3.0 obligations remain. No cloud network calls, recording changes, or production release.

Validation: run :app:testDebugUnitTest and :app:assembleDebug; compare cold and cached runs with the same recording/model; verify source timestamps and cancellation on device. No claimed speedup for uncached inference.
