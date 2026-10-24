package ma.emsi.tp7;

import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import ma.emsi.tp7.model.Departement;
import ma.emsi.tp7.model.Employe;
import ma.emsi.tp7.util.JpaUtil;
import org.hibernate.stat.Statistics;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        EntityManagerFactory emf = JpaUtil.getEntityManagerFactory();
        Statistics stats = JpaUtil.getStatistics();
        stats.setStatisticsEnabled(true);

        System.out.println("================================================================================");
        System.out.println(" TP 7 : Optimisation des Performances, Probleme N+1 et Cache L2 (JPA/Hibernate)");
        System.out.println("================================================================================\n");

        // 1. Initialisation des donnees de test (3 Departements, 9 Employes)
        Long idDeptInformatique;
        EntityManager emInit = emf.createEntityManager();
        try {
            emInit.getTransaction().begin();

            Departement d1 = new Departement("Informatique", "Batiment A");
            d1.addEmploye(new Employe("Yassine Alami", "Tech Lead", 25000));
            d1.addEmploye(new Employe("Sara Benali", "Dev Backend Java", 16000));
            d1.addEmploye(new Employe("Hamza Idrissi", "Dev Frontend React", 14000));

            Departement d2 = new Departement("Finance", "Batiment B");
            d2.addEmploye(new Employe("Khadija Tazi", "Directrice Financiere", 30000));
            d2.addEmploye(new Employe("Omar Berrada", "Analyste Financier", 17000));

            Departement d3 = new Departement("Ressources Humaines", "Batiment C");
            d3.addEmploye(new Employe("Salma Fassi", "Responsable RH", 20000));
            d3.addEmploye(new Employe("Mehdi Chaoui", "Charge de Recrutement", 13000));

            emInit.persist(d1);
            emInit.persist(d2);
            emInit.persist(d3);

            emInit.getTransaction().commit();
            idDeptInformatique = d1.getId();
            System.out.println("[INITIALISATION] 3 Departements et 7 Employes inseres en base.\n");
        } finally {
            emInit.close();
        }

        // 2. Demonstration du Probleme N+1
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(" [DEMONSTRATION 1] Le Probleme N+1 (Requete naive sans optimisation)");
        System.out.println("--------------------------------------------------------------------------------");
        stats.clear();

        EntityManager emNPlusOne = emf.createEntityManager();
        try {
            System.out.println("-> Execution de la requete principale : SELECT d FROM Departement d");
            List<Departement> depts = emNPlusOne.createQuery("SELECT d FROM Departement d", Departement.class)
                    .getResultList();

            System.out.println("-> Iteration sur chaque departement pour acceder aux employes (Lazy Loading) :");
            for (Departement d : depts) {
                // Acces a la collection 'employes' declenche une requete supplementaire SELECT pour chaque departement !
                System.out.println("   * " + d.getNom() + " -> " + d.getEmployes().size() + " employes");
            }

            long totalQueries = stats.getPrepareStatementCount();
            System.out.println("\n[BILAN N+1] Nombre total de requetes SQL executees : " + totalQueries);
            System.out.println("   (1 requete pour les departements + " + (totalQueries - 1) + " requetes pour charger les employes)");
        } finally {
            emNPlusOne.close();
        }

        // 3. Solution 1 au Probleme N+1 : JOIN FETCH
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println(" [DEMONSTRATION 2] Solution 1 : Suppression du N+1 avec JOIN FETCH");
        System.out.println("--------------------------------------------------------------------------------");
        stats.clear();

        EntityManager emJoinFetch = emf.createEntityManager();
        try {
            System.out.println("-> Execution : SELECT DISTINCT d FROM Departement d LEFT JOIN FETCH d.employes");
            List<Departement> depts = emJoinFetch.createQuery(
                    "SELECT DISTINCT d FROM Departement d LEFT JOIN FETCH d.employes", Departement.class)
                    .getResultList();

            for (Departement d : depts) {
                System.out.println("   * " + d.getNom() + " -> " + d.getEmployes().size() + " employes");
            }

            long totalQueries = stats.getPrepareStatementCount();
            System.out.println("\n[BILAN JOIN FETCH] Nombre total de requetes SQL executees : " + totalQueries + " (Requete unique !)");
        } finally {
            emJoinFetch.close();
        }

        // 4. Solution 2 au Probleme N+1 : Entity Graph
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println(" [DEMONSTRATION 3] Solution 2 : Suppression du N+1 avec Entity Graph");
        System.out.println("--------------------------------------------------------------------------------");
        stats.clear();

        EntityManager emGraph = emf.createEntityManager();
        try {
            EntityGraph<?> entityGraph = emGraph.getEntityGraph("Departement.avecEmployes");

            System.out.println("-> Execution avec hint 'jakarta.persistence.fetchgraph' applique a la requete");
            List<Departement> depts = emGraph.createQuery("SELECT d FROM Departement d", Departement.class)
                    .setHint("jakarta.persistence.fetchgraph", entityGraph)
                    .getResultList();

            for (Departement d : depts) {
                System.out.println("   * " + d.getNom() + " -> " + d.getEmployes().size() + " employes");
            }

            long totalQueries = stats.getPrepareStatementCount();
            System.out.println("\n[BILAN ENTITY GRAPH] Nombre total de requetes SQL executees : " + totalQueries + " (Requete unique !)");
        } finally {
            emGraph.close();
        }

        // 5. Demonstration du Cache de Second Niveau (Cache L2)
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println(" [DEMONSTRATION 4] Cache de Second Niveau (Cache L2 / JCache Ehcache)");
        System.out.println("--------------------------------------------------------------------------------");
        stats.clear();

        System.out.println("-> Session 1 (EntityManager 1) : Premier chargement de Departement id = " + idDeptInformatique);
        EntityManager em1 = emf.createEntityManager();
        try {
            Departement d1 = em1.find(Departement.class, idDeptInformatique);
            System.out.println("   Entite chargee : " + d1.getNom());
        } finally {
            em1.close(); // Fermeture de em1 -> Vidage complet du cache de premier niveau (L1)
            System.out.println("   Fermeture de Session 1 (Cache L1 detruit).");
        }

        System.out.println("\n-> Session 2 (EntityManager 2 distinct) : Deuxieme chargement du meme Departement...");
        EntityManager em2 = emf.createEntityManager();
        try {
            Departement d2 = em2.find(Departement.class, idDeptInformatique);
            System.out.println("   Entite chargee : " + d2.getNom());
        } finally {
            em2.close();
        }

        System.out.println("\n[STATISTIQUES DU CACHE L2] :");
        System.out.println("-> Cache L2 Hit Count  (Trouve dans le cache)  : " + stats.getSecondLevelCacheHitCount());
        System.out.println("-> Cache L2 Miss Count (Non trouve en cache)   : " + stats.getSecondLevelCacheMissCount());
        System.out.println("-> Cache L2 Put Count  (Mis en cache)          : " + stats.getSecondLevelCachePutCount());

        JpaUtil.close();
        System.out.println("\n================================================================================");
        System.out.println(" Fin de la demonstration du TP 7 avec succes.");
        System.out.println("================================================================================");
    }
}
