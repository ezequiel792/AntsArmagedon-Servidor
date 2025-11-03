package partida;

import Fisicas.Borde;
import Fisicas.Fisica;
import Fisicas.Mapa;
import Gameplay.Gestores.Logicos.*;
import com.badlogic.gdx.math.Vector2;
import com.principal.Jugador;
import entidades.personajes.Personaje;
import entidades.personajes.tiposPersonajes.HormigaExploradora;
import entidades.personajes.tiposPersonajes.HormigaGuerrera;
import entidades.personajes.tiposPersonajes.HormigaObrera;
import partida.offline.GestorJuegoOffline;
import partida.online.GestorJuegoOnline;

import java.util.ArrayList;
import java.util.List;

public final class FabricaPartida {

    private FabricaPartida() {}

    public static GestorJuegoOnline crearGestorPartidaOnline(
        ConfiguracionPartida config,
        Mapa mapa
    ) {
        Fisica fisica = new Fisica();
        GestorColisiones colisiones = new GestorColisiones(mapa);
        GestorFisica gestorFisica = new GestorFisica(fisica, colisiones);
        GestorProyectiles proyectiles = new GestorProyectiles(colisiones, gestorFisica);
        GestorSpawn spawn = new GestorSpawn(mapa);

        spawn.precalcularPuntosValidos(16f, 16f);
        new Borde(colisiones);

        List<Jugador> jugadores = crearJugadoresOnline(config, colisiones, proyectiles);

        return new GestorJuegoOnline(
            jugadores,
            colisiones,
            proyectiles,
            spawn,
            fisica,
            config.getTiempoTurno(),
            config.getFrecuenciaPowerUps(),
            mapa
        );
    }

    public static GestorJuegoOffline crearGestorPartidaOffline(
        ConfiguracionPartida config,
        Mapa mapa
    ) {
        Fisica fisica = new Fisica();
        GestorColisiones colisiones = new GestorColisiones(mapa);
        GestorFisica gestorFisica = new GestorFisica(fisica, colisiones);
        GestorProyectiles proyectiles = new GestorProyectiles(colisiones, gestorFisica);
        GestorSpawn spawn = new GestorSpawn(mapa);

        spawn.precalcularPuntosValidos(16f, 16f);
        new Borde(colisiones);

        int totalHormigas = Math.max(
            config.getEquipoJugador1().size(),
            config.getEquipoJugador2().size()
        );

        List<Vector2> spawns = spawn.generarVariosSpawnsPersonajes(totalHormigas * 2, 16f, 16f, 60f);

        Jugador jugador1 = crearJugadorOffline(
            0, config.getEquipoJugador1(),
            spawns.subList(0, totalHormigas),
            colisiones, proyectiles
        );

        Jugador jugador2 = crearJugadorOffline(
            1, config.getEquipoJugador2(),
            spawns.subList(totalHormigas, totalHormigas * 2),
            colisiones, proyectiles
        );

        List<Jugador> jugadores = List.of(jugador1, jugador2);

        return new GestorJuegoOffline(
            jugadores,
            colisiones,
            proyectiles,
            spawn,
            fisica,
            config.getTiempoTurno(),
            config.getFrecuenciaPowerUps()
        );
    }

    private static List<Jugador> crearJugadoresOnline(
        ConfiguracionPartida config,
        GestorColisiones gestorColisiones,
        GestorProyectiles gestorProyectiles
    ) {
        List<Jugador> jugadores = new ArrayList<>();
        config.normalizarEquipos();

        float[][] posiciones = {
            {150f, 200f},
            {1050f, 200f}
        };

        jugadores.add(crearJugadorOnline(
            0, config.getEquipoJugador1(),
            gestorColisiones, gestorProyectiles, posiciones[0]
        ));

        jugadores.add(crearJugadorOnline(
            1, config.getEquipoJugador2(),
            gestorColisiones, gestorProyectiles, posiciones[1]
        ));

        return jugadores;
    }

    private static Jugador crearJugadorOnline(
        int idJugador,
        List<String> nombresHormigas,
        GestorColisiones gestorColisiones,
        GestorProyectiles gestorProyectiles,
        float[] posBase
    ) {
        Jugador jugador = new Jugador(idJugador, new ArrayList<>());
        float offset = 50f;

        for (int i = 0; i < nombresHormigas.size(); i++) {
            String tipo = nombresHormigas.get(i);
            if (tipo == null) continue;

            float x = posBase[0] + (i * offset);
            float y = posBase[1];

            Personaje p = crearPersonajeDesdeTipo(tipo, gestorColisiones, gestorProyectiles, x, y, idJugador);
            if (p != null) jugador.agregarPersonaje(p);
        }

        return jugador;
    }

    private static Jugador crearJugadorOffline(
        int idJugador,
        List<String> nombresHormigas,
        List<Vector2> posiciones,
        GestorColisiones gestorColisiones,
        GestorProyectiles gestorProyectiles
    ) {
        Jugador jugador = new Jugador(idJugador, new ArrayList<>());

        for (int i = 0; i < nombresHormigas.size() && i < posiciones.size(); i++) {
            String tipo = nombresHormigas.get(i);
            if (tipo == null) continue;

            Vector2 pos = posiciones.get(i);
            Personaje p = crearPersonajeDesdeTipo(tipo, gestorColisiones, gestorProyectiles, pos.x, pos.y, idJugador);
            if (p != null) jugador.agregarPersonaje(p);
        }

        return jugador;
    }

    private static Personaje crearPersonajeDesdeTipo(
        String tipo,
        GestorColisiones gestorColisiones,
        GestorProyectiles gestorProyectiles,
        float x, float y, int idJugador
    ) {
        return switch (tipo) {
            case "Cuadro_HO_Up" -> new HormigaObrera(gestorColisiones, gestorProyectiles, x, y, idJugador);
            case "Cuadro_HG_Up" -> new HormigaGuerrera(gestorColisiones, gestorProyectiles, x, y, idJugador);
            case "Cuadro_HE_Up" -> new HormigaExploradora(gestorColisiones, gestorProyectiles, x, y, idJugador);
            default -> {
                System.err.println("[FabricaPartida] Tipo de hormiga desconocido: " + tipo);
                yield null;
            }
        };
    }
}
