"""Integración mínima con la API de administración de Keycloak."""

import httpx
from fastapi import HTTPException, status

from database import settings


class KeycloakAdmin:
    def __init__(self):
        self.url = settings.keycloak_url.rstrip("/")
        self.realm = settings.keycloak_realm
        self.admin_user = settings.keycloak_admin_user
        self.admin_password = settings.keycloak_admin_password

    def _token(self) -> str:
        url = f"{self.url}/realms/master/protocol/openid-connect/token"
        data = {
            "grant_type": "password",
            "client_id": "admin-cli",
            "username": self.admin_user,
            "password": self.admin_password,
        }
        try:
            resp = httpx.post(url, data=data, timeout=10)
            resp.raise_for_status()
        except httpx.HTTPStatusError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"No se pudo autenticar con Keycloak Admin: {exc.response.text}",
            )
        except Exception as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=f"Error de red al autenticar Keycloak Admin: {exc}",
            )

        payload = resp.json()
        return payload.get("access_token")

    def _headers(self) -> dict:
        token = self._token()
        return {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        }

    def _get_roles(self, role: str) -> list[dict]:
        """Obtiene las definiciones de roles para asignar según el rol principal."""
        role_hierarchy = {
            "viewer": ["viewer"],
            "operator": ["operator", "viewer"],
            "admin": ["admin", "operator", "viewer"],
        }
        roles_to_assign = role_hierarchy.get(role, [role])  # fallback al rol único si no está definido
        role_defs = []
        for r in roles_to_assign:
            role_defs.append(self._get_role(r))
        return role_defs

    def create_user(
        self,
        username: str,
        password: str,
        role: str,
        email: str | None = None,
        first_name: str | None = None,
        last_name: str | None = None,
    ) -> str:
        url = f"{self.url}/admin/realms/{self.realm}/users"
        payload = {
            "username": username,
            "enabled": True,
            "emailVerified": True,
            "credentials": [
                {"type": "password", "value": password, "temporary": False}
            ],
        }
        if email:
            payload["email"] = email
        if first_name:
            payload["firstName"] = first_name
        if last_name:
            payload["lastName"] = last_name

        resp = httpx.post(url, json=payload, headers=self._headers(), timeout=10)
        if resp.status_code == 409:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="El usuario ya existe en Keycloak",
            )
        if resp.status_code not in (201, 204):
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=f"No se pudo crear el usuario en Keycloak: {resp.text}",
            )

        # Keycloak responde con Location: .../users/{id}
        location = resp.headers.get("Location")
        if not location:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail="No se recibió el identificador del usuario creado",
            )
        user_id = location.rstrip("/").split("/")[-1]

        # Asignar roles al usuario
        role_defs = self._get_roles(role)
        assign_url = f"{self.url}/admin/realms/{self.realm}/users/{user_id}/role-mappings/realm"
        assign_resp = httpx.post(assign_url, json=role_defs, headers=self._headers(), timeout=10)
        if assign_resp.status_code not in (204,):
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=f"No se pudo asignar los roles al usuario: {assign_resp.text}",
            )

        return user_id
