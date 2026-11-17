package juego;

import java.util.List;

/**
 * Algo que pasó durante un turno, para mostrarlo en la bitácora y animarlo en el mapa.
 *
 * @param tipo  categoría (CONFIANZA, ALIANZA, FORTALECE, TRAICION, CONFLICTO, MEDIACION,
 *              CALMA, ALERTA, CRISIS, RESUELTA, MANDO)
 * @param texto descripción para la bitácora
 * @param ids   sobrevivientes involucrados
 * @param sala  sala donde ocurrió ({@code null} si fue en todo el refugio)
 */
public record Evento(String tipo, String texto, List<Integer> ids, Sala sala) {
    /** Copia defensiva de la lista de IDs. */
    public Evento {
        ids = List.copyOf(ids);
    }
}
