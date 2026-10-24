package ma.emsi.tp7;

import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import ma.emsi.tp7.model.Departement;
import ma.emsi.tp7.model.Employe;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PerformanceOptimizationTest {

    private static EntityManagerFactory emf;
    private static Statistics stats;
    private Long idDeptTest;

    @BeforeAll
    public static void setUpAll() {
        emf = Persistence.createEntityManagerFactory("performancePU");
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
    }

    @AfterAll
    public static void tearDownAll() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @BeforeEach
    public void setUp() {
        stats.clear();
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();

            Departement d = new Departement("Departement R&D", "Casablanca Nearshore");
            d.addEmploye(new Employe("Karim Bennani", "Ingenieur R&D", 22000));
            d.addEmploye(new Employe("Imane Bennis", "Data Scientist", 19000));

            em.persist(d);
            em.getTransaction().commit();
            idDeptTest = d.getId();
        } finally {
            em.close();
        }
        stats.clear();
    }

    @Test
    @DisplayName("Validation de la resolution du probleme N+1 via JOIN FETCH (Requete unique)")
    public void testResolutionNPlusOneWithJoinFetch() {
        EntityManager em = emf.createEntityManager();
        try {
            List<Departement> list = em.createQuery(
                    "SELECT DISTINCT d FROM Departement d LEFT JOIN FETCH d.employes", Departement.class)
                    .getResultList();

            // Parcourir toutes les collections chargees
            for (Departement d : list) {
                assertThat(d.getEmployes()).isNotEmpty();
            }

            // Verifier qu'une seule requete SQL a ete preparee
            assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            em.close();
        }
    }

    @Test
    @DisplayName("Validation de la resolution du probleme N+1 via EntityGraph (Requete unique)")
    public void testResolutionNPlusOneWithEntityGraph() {
        EntityManager em = emf.createEntityManager();
        try {
            EntityGraph<?> graph = em.getEntityGraph("Departement.avecEmployes");

            List<Departement> list = em.createQuery("SELECT d FROM Departement d", Departement.class)
                    .setHint("jakarta.persistence.fetchgraph", graph)
                    .getResultList();

            for (Departement d : list) {
                assertThat(d.getEmployes()).isNotEmpty();
            }

            assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        } finally {
            em.close();
        }
    }

    @Test
    @DisplayName("Validation du Cache de Second Niveau (L2 Cache Hit entre EntityManagers distincts)")
    public void testSecondLevelCacheHitAcrossSessions() {
        stats.clear();

        // 1ere session : Cache miss (lecture depuis la base)
        EntityManager em1 = emf.createEntityManager();
        try {
            Departement d1 = em1.find(Departement.class, idDeptTest);
            assertThat(d1).isNotNull();
            assertThat(d1.getNom()).isEqualTo("Departement R&D");
        } finally {
            em1.close(); // Ferme la session 1 (cache L1 libere)
        }

        long initialHits = stats.getSecondLevelCacheHitCount();

        // 2eme session : Cache hit (lecture depuis le cache L2, aucune requete SQL SELECT)
        EntityManager em2 = emf.createEntityManager();
        try {
            Departement d2 = em2.find(Departement.class, idDeptTest);
            assertThat(d2).isNotNull();
            assertThat(d2.getNom()).isEqualTo("Departement R&D");

            // Verifie que le compteur de Hit L2 a augmente
            assertThat(stats.getSecondLevelCacheHitCount()).isGreaterThan(initialHits);
        } finally {
            em2.close();
        }
    }
}
