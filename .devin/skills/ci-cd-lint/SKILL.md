---
name: ci-cd-lint
description: Auditer les workflows GitHub Actions (.github/workflows/*.yml) du projet DevFolio pour les misconfigurations de sécurité CI/CD. COMPLÉMENTAIRE à AGENTS.md (qui couvre déjà : pas de secret dans les workflows, pas de tag latest, scan bloquant, build avant test). Cette skill ajoute ce que AGENTS.md ne couvre pas : actions non épinglées par SHA, continue-on-error sur les scans, environment manquant pour la prod, permissions excessives, script injection. Utiliser quand l'utilisateur modifie .github/workflows/, demande "revue CI/CD", "audit pipeline", "workflow safe", ou avant de merger un changement sur ci-cd-pipeline qui touche le pipeline.
allowed-tools:
  - read
  - grep
  - glob
  - exec
---

# CI/CD Lint

AGENTS.md couvre déjà les règles CI/CD de base (pas de secret, pas de latest, scan bloquant,
build avant test). Cette skill ne les répète pas. Elle vérifie les misconfigurations
spécifiques aux workflows GitHub Actions que AGENTS.md ne mentionne pas.

## Procédure

1. **Cibler** : lire tous les fichiers dans `.github/workflows/`.
2. **Vérifier** : appliquer la checklist ci-dessous sur chaque workflow.
3. **Rapporter** : utiliser le format en fin de skill.

## Misconfigurations à détecter

### Bloquantes (contradiction avec AGENTS.md ou faille critique)

- **`continue-on-error: true` sur Trivy** — Trivy doit être bloquant (`exit-code: '1'`).
  Si `continue-on-error` apparaît sur un step Trivy, le scan d'image est décoratif.
  Exception : Semgrep (SAST) est non-bloquant par design dans ce projet
  (`continue-on-error: true` sur Semgrep = correct, ne pas flagger).
- **`pull_request_target`** comme trigger — exécute le workflow avec les secrets
  du repo sur un PR forké. Faille critique d'injection. Utiliser `pull_request`
  (déjà le cas dans ce projet, vérifier que ça ne régresse pas).
- **Secret passé à une action non-épingle** — `secrets.GITHUB_TOKEN` ou
  `secrets.VPS_*` passé à une action en `@v1` (tag mutable). Le tag peut être
  déplacé vers une version malveillante qui exfiltre le secret.

### Avertissements (bonnes pratiques non couvertes par AGENTS.md)

- **Actions non épinglées par SHA** — `actions/checkout@v4`, `docker/build-push-action@v6`
  utilisent des tags mutable. Un attaquant qui compromet le tag injecte du code.
  Recommandation : épingler par SHA de commit (`@<40-char-hash>`).
  Exception tolérée : actions officielles GitHub (`actions/*`) en tag de version majeure.
  Priorité haute : `appleboy/ssh-action` (accès SSH + clé privée) doit être épinglé par SHA.
- **`permissions` excessives** — `actions: write`, `packages: write` sur un job
  qui n'en a pas besoin. Principe du moindre privilège : ne garder que ce que le
  job utilise réellement.
- **`permissions` au niveau workflow** (hors job) — accorde les permissions à tous
  les jobs. Préférer `permissions:` au niveau de chaque job.
- **Script injection dans `run:`** — interpolation `${{ }}` directement dans un
  bloc `run:` avec des données non fiables (titre de PR, body, branche).
  Utiliser des variables d'environnement (`env:`) puis `$VAR` dans le script.

## Déjà correct dans ce projet (ne pas flagger)

- `pull_request` (pas `pull_request_target`) ✅
- `permissions:` au niveau job (pas workflow) ✅
- `IMAGE_TAG: ${{ github.sha }}` (pas de `latest`) ✅
- Secrets via `${{ secrets.* }}` (pas hardcodés) ✅
- Script deploy utilise `env:` puis `$VAR` (pas d'interpolation directe) ✅
- `continue-on-error: true` sur Semgrep (non-bloquant par design, rapport SARIF
  uploadé pour analyse) ✅
- `appleboy/ssh-action` épinglé par SHA `7eaf766...` (v1.2.0) ✅

## Rapporter

```
## Revue CI/CD : [fichier(s) audité(s)]

### Bloquants (contradiction AGENTS.md ou faille critique)
- [fichier:ligne] [description] [correctif suggéré]

### Avertissements (bonnes pratiques)
- [fichier:ligne] [description] [correctif suggéré]

### Conforme
- [résumer ce qui a été vérifié et validé]
```

Si aucun bloquant : dis-le explicitement. L'absence de rapport = absence de vérification.

## Patterns grep pour les workflows

```
continue-on-error.*true       # sur Trivy = bloquant (à flagger). Sur Semgrep = OK par design
pull_request_target           # faille critique si présent
uses:.*@v[0-9]                # action en tag mutable (avertissement)
uses:.*@main                  # action en branche (critique)
uses:.*@latest                # action en latest (critique)
\$\{\{.*\}\}.*run:            # interpolation dans run: = script injection potentielle
permissions:.*write           # vérifier si justifié
```
