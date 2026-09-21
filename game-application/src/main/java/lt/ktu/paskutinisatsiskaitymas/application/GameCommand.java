package lt.ktu.paskutinisatsiskaitymas.application;

/** Application-owned commands accepted by the authoritative game loop. */
public sealed interface GameCommand permits JoinPlayerCommand, LeavePlayerCommand, InputCommand {
}
