# Freedom Diffusion

Local Android Stable Diffusion 1.5 prototype. **Alpha: real device generation has not yet been validated.**

GitHub Actions builds a native ARM64 CPU-only stable-diffusion.cpp executable and Kotlin app. Install the debug APK from Actions artifacts, then download 2.13GB FP16 SD1.5 weights inside the app (with resume and SHA-256 verification), enter a prompt and generate a 384x384 PNG stored in Pictures/FreedomDiffusion. No image-generation requests leave the device.

Realme 11 Pro / Dimensity 7050: CPU inference could be very slow or run out of RAM. Keep the app foregrounded. Debug APK is not a production signed release. Model license: CreativeML Open RAIL-M. Engine code: leejet/stable-diffusion.cpp, MIT.
