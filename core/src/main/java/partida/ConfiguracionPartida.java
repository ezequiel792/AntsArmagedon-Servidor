package partida;

import java.util.*;

public final class ConfiguracionPartida {

    public static final int[] OPCIONES_TIEMPO_TURNO = {15, 25, 30};
    public static final int[] OPCIONES_FRECUENCIA_PU = {1, 2, 3};

    public static final String[] TIPOS_HORMIGAS = {
        "Cuadro_HO_Up",
        "Cuadro_HG_Up",
        "Cuadro_HE_Up"
    };

    private int indiceMapa = 0;
    private int tiempoTurno = OPCIONES_TIEMPO_TURNO[0];
    private int frecuenciaPowerUps = OPCIONES_FRECUENCIA_PU[0];

    private List<String> equipoJugador1 = new ArrayList<>();
    private List<String> equipoJugador2 = new ArrayList<>();

    public void setMapa(int indice) {
        this.indiceMapa = indice;
        System.out.println("[CONFIG] Mapa seleccionado: " + indice);
    }

    public void setTiempoTurnoPorIndice(int indice) {
        if (indice >= 0 && indice < OPCIONES_TIEMPO_TURNO.length) {
            tiempoTurno = OPCIONES_TIEMPO_TURNO[indice];
            System.out.println("[CONFIG] Tiempo por turno: " + tiempoTurno + "s");
        }
    }

    public void setFrecuenciaPowerUpsPorIndice(int indice) {
        if (indice >= 0 && indice < OPCIONES_FRECUENCIA_PU.length) {
            frecuenciaPowerUps = OPCIONES_FRECUENCIA_PU[indice];
            System.out.println("[CONFIG] Frecuencia PowerUps: cada " + frecuenciaPowerUps + " turnos");
        }
    }

    public void setHormiga(int jugador, int slot, int indiceHormiga) {
        List<String> equipo = (jugador == 1) ? equipoJugador1 : equipoJugador2;
        while (equipo.size() <= slot) equipo.add(null);

        if (indiceHormiga == -1) {
            equipo.set(slot, null);
            return;
        }

        if (indiceHormiga >= 0 && indiceHormiga < TIPOS_HORMIGAS.length)
            equipo.set(slot, TIPOS_HORMIGAS[indiceHormiga]);
        else
            equipo.set(slot, null);
    }

    public void normalizarEquipos() {
        removerNulos(equipoJugador1);
        removerNulos(equipoJugador2);

        int cant1 = equipoJugador1.size();
        int cant2 = equipoJugador2.size();
        Random r = new Random();

        if (cant1 == 0 && cant2 == 0) {
            for (int i = 0; i < 6; i++) {
                equipoJugador1.add(TIPOS_HORMIGAS[r.nextInt(TIPOS_HORMIGAS.length)]);
                equipoJugador2.add(TIPOS_HORMIGAS[r.nextInt(TIPOS_HORMIGAS.length)]);
            }
        } else if (cant1 > 0 && cant2 == 0) {
            for (int i = 0; i < cant1; i++)
                equipoJugador2.add(TIPOS_HORMIGAS[r.nextInt(TIPOS_HORMIGAS.length)]);
        } else if (cant2 > 0 && cant1 == 0) {
            for (int i = 0; i < cant2; i++)
                equipoJugador1.add(TIPOS_HORMIGAS[r.nextInt(TIPOS_HORMIGAS.length)]);
        }
    }

    private void removerNulos(List<String> equipo) {
        equipo.removeIf(Objects::isNull);
    }

    public static ConfiguracionPartida desdeString(String data) {
        ConfiguracionPartida config = new ConfiguracionPartida();
        if (data == null || data.isEmpty()) return config;

        try {
            String[] partes = data.split(":");
            if (partes.length > 0) config.indiceMapa = Integer.parseInt(partes[0]);
            if (partes.length > 1) config.tiempoTurno = Integer.parseInt(partes[1]);
            if (partes.length > 2) config.frecuenciaPowerUps = Integer.parseInt(partes[2]);
            if (partes.length > 3 && !partes[3].isEmpty())
                config.equipoJugador1 = new ArrayList<>(Arrays.asList(partes[3].split(",")));
            if (partes.length > 4 && !partes[4].isEmpty())
                config.equipoJugador2 = new ArrayList<>(Arrays.asList(partes[4].split(",")));
        } catch (Exception e) {
            System.err.println("[CONFIG] Error al parsear configuración desde string: " + e.getMessage());
        }

        return config;
    }

    public String toNetworkString() {
        return indiceMapa + ":" + tiempoTurno + ":" + frecuenciaPowerUps + ":" +
            String.join(",", equipoJugador1) + ":" +
            String.join(",", equipoJugador2);
    }

    public void setDatosDesde(ConfiguracionPartida otra) {
        this.indiceMapa = otra.indiceMapa;
        this.tiempoTurno = otra.tiempoTurno;
        this.frecuenciaPowerUps = otra.frecuenciaPowerUps;
        this.equipoJugador1 = new ArrayList<>(otra.equipoJugador1);
        this.equipoJugador2 = new ArrayList<>(otra.equipoJugador2);
    }

    public int getIndiceMapa() { return indiceMapa; }
    public int getTiempoTurno() { return tiempoTurno; }
    public int getFrecuenciaPowerUps() { return frecuenciaPowerUps; }
    public List<String> getEquipoJugador1() { return equipoJugador1; }
    public List<String> getEquipoJugador2() { return equipoJugador2; }
}
