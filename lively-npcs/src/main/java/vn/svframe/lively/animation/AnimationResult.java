package vn.svframe.lively.animation;

/** Result returned by a body after an animation request has been validated and dispatched. */
public record AnimationResult(boolean accepted, String animation, String detail) {
    public static AnimationResult played(String animation, String detail) {
        return new AnimationResult(true, animation, detail == null ? "" : detail);
    }

    public static AnimationResult unsupported(String animation, String detail) {
        return new AnimationResult(false, animation, detail == null ? "unsupported animation" : detail);
    }
}
