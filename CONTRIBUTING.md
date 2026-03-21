# Contributing to AetherSuite

Thank you for taking the time to look at this project.

AetherSuite is open source and welcomes contributions from anyone. You do not need to be an expert developer to help. Reporting a bug, translating a text, or suggesting a missing feature are all valuable contributions.

---

## Ways to contribute

**Report a bug**

If something does not work as expected, open an issue using the Bug Report template. Describe what you did, what you expected to happen, and what actually happened. The more detail you give, the faster it can be fixed.

**Suggest a feature**

If you have an idea for something the apps could do better or differently, open an issue using the Feature Request template. Explain what the feature is and why it would be useful.

**Submit code**

If you want to fix a bug or add a feature yourself, follow the steps below.

---

## How to submit code

1. Fork this repository by clicking the "Fork" button at the top right of this page.

2. Clone your fork to your computer :

```bash
git clone https://github.com/YOUR-USERNAME/AetherSuite.git
```

3. Create a new branch with a short, descriptive name :

```bash
git checkout -b fix-photo-quality
```

4. Make your changes.

5. Test your changes on a real Android device before submitting.

6. Commit your changes with a clear message :

```bash
git commit -m "Fix MMS photo compression for large images"
```

7. Push your branch :

```bash
git push origin fix-photo-quality
```

8. Open a Pull Request from your fork to this repository. Fill in the Pull Request template.

---

## Code style

- This project uses Kotlin and Jetpack Compose.
- Follow the existing code structure and naming conventions.
- Keep functions short and focused on one thing.
- Add comments when the intent is not obvious.
- Do not add dependencies without a clear reason.

---

## What gets accepted

Pull requests are more likely to be accepted if they :

- Fix a real, reproducible bug
- Add something that was clearly missing
- Do not break existing functionality
- Include a clear description of what changed and why
- Are reasonably small and focused

Large refactors or architecture changes should be discussed in an issue first.

---

## Questions

If you are unsure about anything, open an issue and ask. There are no bad questions here.
