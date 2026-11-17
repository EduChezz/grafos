# Grafos · APE 4: Relaciones sociales entre estudiantes

Aplicación en Java que gestiona y analiza las relaciones de amistad entre los estudiantes de un curso usando un **grafo dirigido con listas de adyacencia**.

Incluye dos interfaces sobre el mismo motor:

- **Nexo** (`http://localhost:8080/`): vista técnica del grafo, listas de adyacencia, recorridos y análisis.
- **Refugio AP-4** (`http://localhost:8080/refugio`): juego de simulación social donde los algoritmos del grafo tienen un propósito dentro de la historia.

## Qué implementa

| Punto | Implementación |
| --- | --- |
| Representación | Listas de adyacencia: cada vértice guarda sus arcos salientes y entrantes por referencia |
| Vértices | Estudiantes con nombre, carrera, semestre, edad y correo |
| Relaciones | Arcos dirigidos (la amistad puede no ser recíproca) con tipo y afinidad |
| Operaciones | Agregar y eliminar estudiantes y relaciones |
| Recorridos | BFS (cola) y DFS (pila), camino más corto con BFS y camino de mayor afinidad con Dijkstra |
| Análisis | Amigos en común, sugerencias, comunidades con su fuerza (densidad n·(n−1)/2) e influencia (grado e intermediación) |
| Juego | Ruta SOS (BFS) y Alerta de motín (DFS + Kosaraju) |

## Cómo ejecutarlo

Requisitos: Java 17 o superior y Maven.

```bash
mvn compile exec:java
```

Luego abre `http://localhost:8080` (Nexo) o `http://localhost:8080/refugio` (el juego). El mismo programa ofrece un menú de consola.

## Estructura

```
src/main/java/
  modelo/    Grafo, Usuario (vértice), Arco, Arista, PerfilEstudiante, TipoRelacion
  analisis/  AnalizadorRed (BFS, DFS, caminos, comunidades), AnalizadorInfluencia
  datos/     CargadorJson
  juego/     Refugio (motor de turnos), AnalizadorRefugio (Ruta SOS, motín), ApiRefugio
  web/       ServidorWeb (servidor HTTP y API JSON)
src/main/resources/
  web/       index.html (Nexo), refugio.html (Refugio AP-4)
  ejemplos/  redes de ejemplo en JSON
```

El manual del juego está en `Manual_Refugio_AP-4.pdf`.
