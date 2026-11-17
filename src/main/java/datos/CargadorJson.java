package datos;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import modelo.Arco;
import modelo.Grafo;
import modelo.PerfilEstudiante;
import modelo.TipoRelacion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;

/**
 * Carga un grafo desde JSON (con Gson) y valida cada campo con mensajes claros.
 * <p>
 * Formato:
 * <pre>
 * {
 *   "usuarios": [
 *     { "id": 1, "nombre": "Ana", "carrera": "Software", "semestre": 3,
 *       "edad": 20, "correo": "ana@uni.edu" }
 *   ],
 *   "relaciones": [
 *     { "a": 1, "b": 2, "tipoAB": "AMISTAD", "tipoBA": "AMISTAD", "pesoAB": 4, "pesoBA": 2 }
 *   ]
 * }
 * </pre>
 * Son obligatorios {@code usuarios[].id}, {@code usuarios[].nombre}, {@code relaciones[].a},
 * {@code relaciones[].b} y {@code relaciones[].tipoAB}. Lo demás es opcional.
 * Si hay cualquier error se lanza {@link IllegalArgumentException} y no se devuelve un grafo a medias.
 */
public class CargadorJson {

    /**
     * @param ruta ruta del archivo JSON
     * @return el grafo cargado
     * @throws IllegalArgumentException si el archivo no existe o tiene errores
     */
    public Grafo cargarDesdeArchivo(String ruta) {
        if (ruta == null || ruta.trim().isEmpty()) {
            throw new IllegalArgumentException("La ruta del archivo está vacía");
        }
        Path path = Paths.get(ruta.trim());
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("El archivo no existe: " + ruta);
        }
        try {
            return cargarDesdeTexto(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo: " + e.getMessage());
        }
    }

    /**
     * @param texto contenido JSON
     * @return el grafo cargado
     * @throws IllegalArgumentException si el JSON tiene errores
     */
    public Grafo cargarDesdeTexto(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            throw new IllegalArgumentException("El contenido JSON está vacío");
        }
        JsonObject raiz = parsearRaiz(texto);
        Grafo grafo = new Grafo();
        cargarUsuarios(raiz, grafo);
        cargarRelaciones(raiz, grafo);
        return grafo;
    }

    private JsonObject parsearRaiz(String texto) {
        JsonElement elemento;
        try {
            elemento = JsonParser.parseString(texto);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("JSON mal formado: " + e.getMessage());
        }
        if (!elemento.isJsonObject()) {
            throw new IllegalArgumentException("El JSON debe ser un objeto con 'usuarios' y 'relaciones'");
        }
        return elemento.getAsJsonObject();
    }

    private void cargarUsuarios(JsonObject raiz, Grafo grafo) {
        JsonArray usuarios = leerArreglo(raiz, "usuarios", true);
        for (int i = 0; i < usuarios.size(); i++) {
            try {
                JsonObject usuario = leerObjeto(usuarios.get(i));
                int id = leerEntero(usuario, "id");
                String nombre = leerTexto(usuario, "nombre");
                grafo.agregarUsuario(id, nombre, leerPerfil(usuario));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Usuario #" + (i + 1) + ": " + e.getMessage());
            }
        }
    }

    private void cargarRelaciones(JsonObject raiz, Grafo grafo) {
        JsonArray relaciones = leerArreglo(raiz, "relaciones", false);
        for (int i = 0; i < relaciones.size(); i++) {
            try {
                JsonObject relacion = leerObjeto(relaciones.get(i));
                int a = leerEntero(relacion, "a");
                int b = leerEntero(relacion, "b");
                TipoRelacion tipoAB = leerTipo(relacion, "tipoAB", null);
                TipoRelacion tipoBA = leerTipo(relacion, "tipoBA", TipoRelacion.DESCONOCIDO);
                Double pesoAB = leerPeso(relacion, "pesoAB");
                Double pesoBA = leerPeso(relacion, "pesoBA");

                grafo.agregarRelacion(a, b, tipoAB, pesoAB != null ? pesoAB : tipoAB.getPesoPorDefecto());
                if (tipoBA != TipoRelacion.DESCONOCIDO) {
                    grafo.cambiarTipoRelacion(b, a, tipoBA);
                }
                if (pesoBA != null) grafo.cambiarPeso(b, a, pesoBA);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Relación #" + (i + 1) + ": " + e.getMessage());
            }
        }
    }

    /**
     * Lee los campos opcionales de perfil de un objeto JSON:
     * {@code carrera}, {@code semestre}, {@code edad} y {@code correo}.
     * También lo usa el servidor web al crear estudiantes.
     *
     * @param objeto objeto JSON del estudiante
     * @return el perfil (vacío si no trae ningún campo)
     * @throws IllegalArgumentException si algún campo tiene un tipo o valor inválido
     */
    public PerfilEstudiante leerPerfil(JsonObject objeto) {
        return new PerfilEstudiante(
            leerTextoOpcional(objeto, "carrera"),
            leerEnteroOpcional(objeto, "semestre"),
            leerEnteroOpcional(objeto, "edad"),
            leerTextoOpcional(objeto, "correo")
        );
    }

    private String leerTextoOpcional(JsonObject objeto, String campo) {
        JsonElement elemento = objeto.get(campo);
        if (elemento == null || elemento.isJsonNull()) return "";
        if (!elemento.isJsonPrimitive() || !elemento.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("el campo '" + campo + "' debe ser texto");
        }
        return elemento.getAsString();
    }

    private int leerEnteroOpcional(JsonObject objeto, String campo) {
        JsonElement elemento = objeto.get(campo);
        if (elemento == null || elemento.isJsonNull()) return 0;
        return leerEntero(objeto, campo);
    }

    private JsonArray leerArreglo(JsonObject objeto, String campo, boolean obligatorio) {
        JsonElement elemento = objeto.get(campo);
        if (elemento == null || elemento.isJsonNull()) {
            if (obligatorio) {
                throw new IllegalArgumentException("Falta el campo '" + campo + "'");
            }
            return new JsonArray();
        }
        if (!elemento.isJsonArray()) {
            throw new IllegalArgumentException("El campo '" + campo + "' debe ser una lista");
        }
        return elemento.getAsJsonArray();
    }

    private JsonObject leerObjeto(JsonElement elemento) {
        if (elemento == null || !elemento.isJsonObject()) {
            throw new IllegalArgumentException("debe ser un objeto");
        }
        return elemento.getAsJsonObject();
    }

    private int leerEntero(JsonObject objeto, String campo) {
        JsonElement elemento = objeto.get(campo);
        if (elemento == null || !elemento.isJsonPrimitive() || !elemento.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("falta el campo '" + campo + "' o no es un número");
        }
        double valor = elemento.getAsDouble();
        if (valor != Math.rint(valor) || valor < Integer.MIN_VALUE || valor > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("el campo '" + campo + "' debe ser un número entero");
        }
        return (int) valor;
    }

    private String leerTexto(JsonObject objeto, String campo) {
        JsonElement elemento = objeto.get(campo);
        if (elemento == null || !elemento.isJsonPrimitive() || !elemento.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("falta el campo '" + campo + "' o no es texto");
        }
        String texto = elemento.getAsString();
        if (texto.trim().isEmpty()) {
            throw new IllegalArgumentException("el campo '" + campo + "' no puede estar vacío");
        }
        return texto;
    }

    private TipoRelacion leerTipo(JsonObject objeto, String campo, TipoRelacion porDefecto) {
        JsonElement elemento = objeto.get(campo);
        if (elemento == null || elemento.isJsonNull()) {
            if (porDefecto == null) {
                throw new IllegalArgumentException("falta el campo '" + campo + "'");
            }
            return porDefecto;
        }
        if (!elemento.isJsonPrimitive() || !elemento.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("el campo '" + campo + "' debe ser texto");
        }
        String texto = elemento.getAsString().trim();
        try {
            return TipoRelacion.valueOf(texto.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "tipo de relación desconocido '" + texto + "'. Tipos válidos: "
                + Arrays.toString(TipoRelacion.values())
            );
        }
    }

    private Double leerPeso(JsonObject objeto, String campo) {
        JsonElement elemento = objeto.get(campo);
        if (elemento == null || elemento.isJsonNull()) return null;
        if (!elemento.isJsonPrimitive() || !elemento.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("el campo '" + campo + "' debe ser un número");
        }
        return Arco.validarPeso(elemento.getAsDouble());
    }
}