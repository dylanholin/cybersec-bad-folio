package com.devfolio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Rate limiting simple en mémoire par adresse IP.
 * Utilise une fenêtre glissante : maxAttempts tentées sur windowMs millisecondes.
 * <p>
 * Limitations : non distribué (ne fonctionne pas en cluster), pas de persistence.
 * Pour la production, utiliser Bucket4j, Resilience4j ou Redis.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final int maxAttempts;
    private final long windowMs;

    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public RateLimitService() {
        this(5, 60_000L); // 5 tentatives par minute par IP
    }

    /**
     * Constructeur avec paramètres personnalisables.
     *
     * @param maxAttempts nombre maximum de tentatives dans la fenêtre
     * @param windowMs    durée de la fenêtre en millisecondes
     */
    public RateLimitService(final int maxAttempts, final long windowMs) {
        this.maxAttempts = maxAttempts;
        this.windowMs = windowMs;
    }

    /**
     * Enregistre une tentative et vérifie si la limite est atteinte.
     *
     * @param key identifiant (adresse IP du client)
     * @return true si la limite est atteinte (requête à rejeter), false sinon
     */
    public boolean isRateLimited(final String key) {
        final long now = System.currentTimeMillis();
        final Deque<Long> timestamps = attempts.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        // Supprimer les entrées hors de la fenêtre glissante
        while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > windowMs) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxAttempts) {
            log.warn("Rate limit atteint pour {} : {} tentatives en {}ms", key, timestamps.size(), windowMs);
            return true;
        }

        timestamps.addLast(now);
        return false;
    }

    /**
     * Réinitialise le compteur pour une clé (utile après un login réussi).
     *
     * @param key identifiant à réinitialiser
     */
    public void reset(final String key) {
        attempts.remove(key);
    }
}
