package partida;

import Fisicas.*;
import Fisicas.Mapa;
import Gameplay.Gestores.Logicos.*;
import com.badlogic.gdx.math.Vector2;
import com.principal.Jugador;
import entidades.personajes.Personaje;
import entidades.personajes.tiposPersonajes.*;
import network.ServerThread;
import partida.online.GestorJuegoServidor;

import java.util.ArrayList;
import java.util.List;

public final class FabricaPartidaServidor {

    private FabricaPartidaServidor() {}

    public static GestorJuegoServidor crearGestorPartidaServidor(
        partida.ConfiguracionPartidaServidor config,
        Mapa mapa,
        List<Vector2> spawnsPrecalculados,
        ServerThread serverThread
    ) {
        Fisica fisica = new Fisica();
        GestorColisiones colisiones = new GestorColisiones(mapa);
        GestorFisica gestorFisica = new GestorFisica(fisica, colisiones);
        GestorProyectiles proyectiles = new GestorProyectiles(colisiones, gestorFisica);
        GestorSpawn gestorSpawn = new GestorSpawn(mapa);

        new Borde(colisiones);

        if (spawnsPrecalculados == null || spawnsPrecalculados.isEmpty()) {
            gestorSpawn.precalcularPuntosValidos();
            spawnsPrecalculados = generarSpawns(config, gestorSpawn, colisiones, proyectiles);
        }

        gestorSpawn.setSpawnsIniciales(spawnsPrecalculados);

        List<Jugador> jugadores = crearJugadores(config, spawnsPrecalculados, colisiones, proyectiles);

        return new GestorJuegoServidor(
            jugadores,
            colisiones,
            proyectiles,
            gestorSpawn,
            fisica,
            config.getTiempoTurno(),
            config.getFrecuenciaPowerUps(),
            serverThread
        );
    }

    public static List<Vector2> generarSpawnsPersonajesServidor(
        ConfiguracionPartidaServidor config,
        GestorSpawn gestorSpawn
    ) {
        Mapa mapa = gestorSpawn.getMapa();

        if (mapa == null) {
            throw new IllegalStateException("[FabricaPartidaServidor] El mapa no puede ser nulo para generar spawns.");
        }

        GestorColisiones colisiones = new GestorColisiones(mapa);
        Fisica fisica = new Fisica();
        GestorFisica gestorFisica = new GestorFisica(fisica, colisiones);
        GestorProyectiles proyectiles = new GestorProyectiles(colisiones, gestorFisica);

        gestorSpawn.precalcularPuntosValidos();

        return generarSpawns(config, gestorSpawn, colisiones, proyectiles);
    }

    private static List<Vector2> generarSpawns(
        partida.ConfiguracionPartidaServidor config,
        GestorSpawn gestorSpawn,
        GestorColisiones colisiones,
        GestorProyectiles proyectiles
    ) {
        List<Vector2> posiciones = new ArrayList<>();

        List<String> todas = new ArrayList<>();
        todas.addAll(config.getEquipoJugador1());
        todas.addAll(config.getEquipoJugador2());

        for (String tipo : todas) {
            if (tipo == null) continue;

            Personaje tmp = crearPersonajeDesdeTipo(tipo, colisiones, proyectiles, 0, 0, 0);
            if (tmp == null) continue;

            Vector2 pos = gestorSpawn.generarSpawnEntidad(tmp);
            if (pos == null) {
                gestorSpawn.precalcularPuntosValidos();
                pos = gestorSpawn.generarSpawnEntidad(tmp);
            }

            posiciones.add(pos);
        }

        return posiciones;
    }

    private static List<Jugador> crearJugadores(
        partida.ConfiguracionPartidaServidor config,
        List<Vector2> spawns,
        GestorColisiones colisiones,
        GestorProyectiles proyectiles
    ) {
        config.normalizarEquipos();

        int total1 = config.getEquipoJugador1().size();
        int total2 = config.getEquipoJugador2().size();

        if (spawns.size() < total1 + total2)
            throw new IllegalStateException("[FabricaPartidaServidor] Spawns insuficientes para los personajes.");

        Jugador j1 = crearJugador(0, config.getEquipoJugador1(), spawns.subList(0, total1), colisiones, proyectiles);
        Jugador j2 = crearJugador(1, config.getEquipoJugador2(), spawns.subList(total1, total1 + total2), colisiones, proyectiles);

        return List.of(j1, j2);
    }

    private static Jugador crearJugador(
        int idJugador,
        List<String> nombresHormigas,
        List<Vector2> posiciones,
        GestorColisiones colisiones,
        GestorProyectiles proyectiles
    ) {
        Jugador jugador = new Jugador(idJugador, new ArrayList<>());

        for (int i = 0; i < nombresHormigas.size() && i < posiciones.size(); i++) {
            String tipo = nombresHormigas.get(i);
            if (tipo == null) continue;

            Vector2 pos = posiciones.get(i);
            Personaje p = crearPersonajeDesdeTipo(tipo, colisiones, proyectiles, pos.x, pos.y, idJugador);
            if (p != null) jugador.agregarPersonaje(p);
        }

        return jugador;
    }

    private static Personaje crearPersonajeDesdeTipo(
        String tipo,
        GestorColisiones colisiones,
        GestorProyectiles proyectiles,
        float x, float y,
        int idJugador
    ) {
        return switch (tipo) {
            case "Cuadro_HO_Up" -> new HormigaObrera(colisiones, proyectiles, x, y, idJugador);
            case "Cuadro_HG_Up" -> new HormigaGuerrera(colisiones, proyectiles, x, y, idJugador);
            case "Cuadro_HE_Up" -> new HormigaExploradora(colisiones, proyectiles, x, y, idJugador);
            default -> {
                System.err.println("[FabricaPartidaServidor] Tipo de hormiga desconocido: " + tipo);
                yield null;
            }
        };
    }
}
