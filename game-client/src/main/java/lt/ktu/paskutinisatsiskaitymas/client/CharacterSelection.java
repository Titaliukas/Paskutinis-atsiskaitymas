package lt.ktu.paskutinisatsiskaitymas.client;

import lt.ktu.paskutinisatsiskaitymas.protocol.Hello;

/** Command receiver for the local character choice before joining a server. */
final class CharacterSelection {
    private String selectedCharacter = Hello.DEFAULT_CHARACTER;

    String selectedCharacter() {
        return selectedCharacter;
    }

    void select(String character) {
        if (character == null || !Hello.CHARACTERS.contains(character)) {
            throw new IllegalArgumentException("Unknown character");
        }
        selectedCharacter = character;
    }
}
