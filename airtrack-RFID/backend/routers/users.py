"""routers/users.py — Gestión de usuarios de Keycloak desde el backend."""

from fastapi import APIRouter, Depends, HTTPException, status

from auth import get_current_user, CurrentUser
from keycloak import KeycloakAdmin
from schemas import UserCreate, UserOut

router = APIRouter(prefix="/users", tags=["users"])


@router.post("", response_model=UserOut, status_code=201)
def create_user(
    body: UserCreate,
    user: CurrentUser = Depends(get_current_user),
):
    user.require_role("admin")

    if body.role not in {"admin", "operator", "viewer"}:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Rol inválido. Elija admin, operator o viewer.",
        )

    kc = KeycloakAdmin()
    user_id = kc.create_user(
        username=body.username,
        password=body.password,
        role=body.role,
        email=body.email,
        first_name=body.firstName,
        last_name=body.lastName,
    )

    return UserOut(
        id=user_id,
        username=body.username,
        email=body.email,
        firstName=body.firstName,
        lastName=body.lastName,
        role=body.role,
    )
