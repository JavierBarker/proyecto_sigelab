from app.schemas import EnrollRequest, UserRequest


def test_requests_use_stable_user_id_alias():
    assert EnrollRequest.model_validate({"userId": 7}).user_id == 7
    assert UserRequest.model_validate({"userId": 9}).model_dump(by_alias=True)["userId"] == 9
