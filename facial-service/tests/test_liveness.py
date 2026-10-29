from app.liveness import LivenessSession


def test_liveness_requires_neutral_frames_before_pose_challenge():
    session = LivenessSession(28, 22, supports_blink=False)
    face = object()
    for _ in range(2):
        assert session.observe(face, 0, 0) is False
    assert session.neutral_frames == 2
    assert session.completed is False


def test_liveness_completes_the_selected_turn_challenge():
    session = LivenessSession(28, 22, supports_blink=False)
    face = object()
    for _ in range(3):
        session.observe(face, 0, 0)
    yaw = -16 if session.challenge == "turn_left" else 16
    assert session.observe(face, yaw, 0) is True
    assert session.completed is True
