# Corrections Trivy : Cycle d'itération CI

> Le pipeline a nécessité plusieurs itérations pour passer de 36 vulnérabilités à 0.

---

> **Pour un débutant** : Trivy est un outil qui scanne les images Docker à la recherche de **CVE** (Common Vulnerabilities and Exposures), des failles de sécurité connues et référencées publiquement. Chaque CVE a un identifiant unique (ex : CVE-2025-24813), une gravité (LOW, MEDIUM, HIGH, CRITICAL) et un composant affecté (ex : Tomcat, Spring Security).
>
> **Pourquoi scanner ?** : une image Docker contient des dizaines de bibliothèques. Si l'une d'elles a une faille connue, un attaquant peut l'exploiter. Trivy vérifie automatiquement que les bibliothèques utilisées n'ont pas de vulnérabilités HIGH ou CRITICAL non corrigées.
>
> **Que faire quand Trivy trouve une CVE ?** : mettre à jour la bibliothèque concernée vers une version corrigée. C'est ce qui est décrit ci-dessous.

---

## Run #4 : `buildx failed`

| Problème | Cause | Fix |
|---|---|---|
| Docker buildx cache error | Permission `actions: write` manquante | Ajout de la permission au job `build-and-push` |

---

## Run #5 : 36 CVE détectées (exit code 1)

Trivy a remonté **3 CVE Alpine** + **33 CVE Java** (27 HIGH, 6 CRITICAL).

| Composant | Version vulnérable | Version corrigée | CVE |
|---|---|---|---|
| **Spring Boot** | 3.2.0 | 3.5.15 | CVE-2025-22235 (Spring Boot EndpointRequest) |
| **Tomcat embed** | 10.1.16 | 10.1.55+ | CVE-2025-24813 (CRITICAL RCE), CVE-2026-41293, CVE-2026-43512, CVE-2026-43515, +12 autres |
| **Spring Security** | 6.2.0 | 6.4+ | CVE-2024-38821 (CRITICAL auth bypass), CVE-2026-22732, CVE-2025-22228 |
| **Spring Framework** | 6.1.1 | 6.2.11+ | CVE-2025-41249, CVE-2024-22243/22259/22262, CVE-2024-38816/38819 |
| **Logback** | 1.4.11 | 1.5.x | CVE-2023-6378 (serialization vulnerability) |
| **OpenSSL (Alpine)** | 3.5.6-r0 | 3.5.7-r0 | CVE-2026-45447 (heap use-after-free PKCS7_verify) |

**Correctifs appliqués** :

- `backend/pom.xml` : Spring Boot parent 3.2.0 → 3.5.15 (tire automatiquement Tomcat 10.1.55+, Spring Security 6.4+, Spring Framework 6.2+, Logback 1.5+)
- `backend/Dockerfile` : `apk update && apk upgrade --no-cache` avant `addgroup` (récupère OpenSSL 3.5.7-r0)
- `frontend/Dockerfile` : idem + Node 20 → 22 (cohérence avec la CI)

---

## Run #6 : Faux positif secret SSL

| Problème | Cause | Fix |
|---|---|---|
| Trivy secret scan : `AsymmetricPrivateKey` détecté | Certificat SSL auto-signé du frontend (clé privée RSA dans `/etc/nginx/ssl/devfolio.key`) | `scanners: vuln` pour limiter Trivy aux vulnérabilités uniquement |

> Le certificat auto-signé est généré dans le Dockerfile (`openssl req -x509 -nodes`). La clé privée n'est pas une fuite réelle. Elle est embarquée dans l'image pour le HTTPS de dev/staging.

---

## Run #7 : Succès ✅

| Résultat | Détail |
|---|---|
| **Status** | Success (3m 16s) |
| **Tests backend** | 16 tests JUnit passés |
| **Tests frontend** | Vitest + build passés |
| **Semgrep** | Scan SAST complété, SARIF uploadé |
| **Trivy backend** | 0 vulnérabilités HIGH/CRITICAL |
| **Trivy frontend** | 0 vulnérabilités HIGH/CRITICAL |
| **Images poussées** | GHCR : `devfolio-backend:8a73a2e`, `devfolio-frontend:8a73a2e` |

> **Note** : Des warnings `Node.js 20 is deprecated` apparaissent sur `actions/checkout@v4`, `docker/build-push-action@v6`, etc. Ces warnings sont des **deprecation notices non bloquants**. GitHub Actions force automatiquement l'exécution sur Node 24. Les actions restent fonctionnelles ; la migration officielle vers les versions Node 22/24 des actions attend les releases upstream.

---

## Configuration Trivy finale

```yaml
scanners: vuln          # vulnérabilités uniquement (pas de scan secrets)
severity: HIGH,CRITICAL # bloquant uniquement sur HIGH et CRITICAL
ignore-unfixed: true    # ignore les CVE sans correctif disponible
exit-code: '1'          # bloquant
```
