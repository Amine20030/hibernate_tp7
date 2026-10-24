# TP 7 : Optimisation des Performances, Résolution du Problème N+1 et Cache L2 (JPA / Hibernate)

Ce projet présente de manière concrète et mesurable l'optimisation des performances dans une application d'entreprise avec **Hibernate** et **JPA** :
1. Mise en évidence du problème classique **N+1 Queries**.
2. Élimination du N+1 grâce à **`JOIN FETCH`**.
3. Élimination du N+1 grâce aux **`EntityGraph`** (`@NamedEntityGraph`).
4. Configuration et exploitation du **Cache de Second Niveau (L2 Cache)** avec **JCache (Ehcache)**.

---

## 🎯 Objectifs Pédagogiques

- Comprendre l'impact critique du chargement paresseux (*Lazy Loading*) sur les performances de la base de données (problème N+1).
- Maîtriser le chargement précoce contrôlé avec `JOIN FETCH` en JPQL.
- Utiliser les `EntityGraph` de Jakarta Persistence pour une flexibilité dynamique sans modifier les requêtes JPQL.
- Configurer le cache partagé L2 (`shared-cache-mode: ENABLE_SELECTIVE`, `@Cacheable`, `@Cache`).
- Mesurer les gains de performance à l'aide des statistiques Hibernate (`org.hibernate.stat.Statistics`).

---

## 🏗️ Architecture du Projet

```text
hibernate_tp7/
├── pom.xml
├── README.md
├── .gitignore
└── src/
    ├── main/
    │   ├── java/
    │   │   └── ma/emsi/tp7/
    │   │       ├── model/
    │   │       │   ├── Departement.java   # @Cacheable, @Cache, @NamedEntityGraph
    │   │       │   └── Employe.java       # @Cacheable, @Cache
    │   │       ├── util/
    │   │       │   └── JpaUtil.java       # Gestionnaire EMF & Hibernate Statistics
    │   │       └── Main.java              # Démonstrations pas-à-pas avec métriques
    │   └── resources/
    │       └── META-INF/
    │           └── persistence.xml        # Config JPA + JCache / Ehcache + H2
    └── test/
        └── java/
            └── ma/emsi/tp7/
                └── PerformanceOptimizationTest.java # Tests automatisés JUnit 5
```

---

## 🔍 Analyse Comparative des Techniques

| Approche | Nombre de Requêtes SQL (pour N départements) | Risque de Produit Cartésien | Flexibilité |
|---|---|---|---|
| **Chargement Naïf (Lazy)** | **1 + N requêtes** | Aucun | Faible (performances critiques) |
| **`JOIN FETCH`** | **1 seule requête** | Oui si plusieurs collections | Forte en JPQL |
| **`EntityGraph`** | **1 seule requête** | Contrôlé par les plans de graphes | Très forte (découplée de JPQL) |
| **Cache de Second Niveau (L2)** | **0 requête SQL (sur Cache Hit)** | N/A | Transparente à travers plusieurs sessions |

---

## 🚀 Exécution

### 1. Compiler et Lancer les Tests
```bash
mvn clean test
```

### 2. Lancer la Démonstration Complète
```bash
mvn compile exec:java
```

---

## 🧪 Tests Unitaires Automatisés

- `testResolutionNPlusOneWithJoinFetch` : Valide que le nombre de requêtes SQL préparées est exactement égal à `1`.
- `testResolutionNPlusOneWithEntityGraph` : Valide l'exécution en requête unique avec le graphe d'entités.
- `testSecondLevelCacheHitAcrossSessions` : Vérifie que l'entité est servie directement depuis le cache L2 entre deux `EntityManager` distincts sans émettre de requête SQL supplémentaire.

---

## 👨‍💻 Auteur
- **Mohamed Amine El Hezzam** - Étudiant EMSI
