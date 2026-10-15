package dev.svextract.client;

import com.google.gson.JsonObject;

/** Keeps native lobby submenus current without replacing the screen on asynchronous refresh. */
interface LobbyPanel { void updateLobby(JsonObject state); }
