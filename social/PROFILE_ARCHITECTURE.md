# Animeta — Profile Architecture, REST/GraphQL Contracts & Privacy Specification

## 1. Architectural Overview & Domain Models

The Animeta user profile system is built around a secure, modern Jetpack Compose interface with deep integration into Firebase Authentication, Cloud Firestore, and AniList GraphQL, respecting strict separation of concerns:

- **Public Persona (`users/{userId}`)**: Contains only non-sensitive visual and social fields:
  - `displayName`, `username`, `handle`, `photoUrl`, `bannerUrl`, `bio`, `location`, `websiteUrl`, `customization`, `privacy`.
- **Private Account Data**: Managed via Firebase Auth token claims and isolated private collections (`users/{userId}/private_account`). Email addresses and phone numbers are never stored in public profile documents.
- **Anime Library State**: Stored in `users/{userId}/library/{animeId}` and `users/{userId}/watch_progress/{animeId}`.

---

## 2. REST & GraphQL Integration Contracts

### A. AniList GraphQL Integration
The profile links to public AniList profiles without compromising credentials:
```graphql
query GetViewerProfile {
  Viewer {
    id
    name
    avatar {
      large
      medium
    }
    bannerImage
    about(asHtml: false)
    statistics {
      anime {
        count
        episodesWatched
        minutesWatched
        meanScore
      }
    }
  }
}
```

### B. Cloudinary Image Upload Contract (REST multipart/form-data)
- **Endpoint**: `https://api.cloudinary.com/v1_1/{cloud_name}/image/upload`
- **Method**: `POST`
- **Payload**:
  - `file`: Raw image binary (`image/jpeg`, `image/png`, `image/webp`). Validated on client (max 5MB, strict MIME check).
  - `upload_preset`: Configured unsigned or signed upload preset.
- **Response**:
  ```json
  {
    "public_id": "animite/avatars/user_123",
    "secure_url": "https://res.cloudinary.com/.../image/upload/v.../avatar.webp",
    "format": "webp",
    "width": 512,
    "height": 512
  }
  ```

---

## 3. Privacy, Granular Visibility & Audit Trail

### A. Field-Level Visibility Controls
The `ProfilePrivacy` and `ProfileCustomization` structures enable users to dictate exact visibility:
- **`profileVisibility`**: `PUBLIC`, `FOLLOWERS_ONLY`, `PRIVATE`.
- **`allowMessagesFrom`**: `EVERYONE`, `FOLLOWERS`, `NONE`.
- **`activityVisibility`**: Controls whether episode progress and anime updates generate public activity feed events.

### B. Audit Trail & Security
- **Firestore Security Rules**: Strictly enforces that:
  - Clients cannot inject `email` or `phoneNumber` into `/users/{userId}`.
  - Clients cannot mutate administrative fields (`roles`, `isBanned`).
  - Unauthenticated reads of private conversations and library watch progress are denied.
- **Audit Logging Collection (`audit_logs/{logId}`)**:
  Whenever sensitive profile attributes (email changes, password resets, account deletion requests) occur, an immutable record is generated with:
  - `actorUid`, `targetUid`, `action` (`PROFILE_UPDATED`, `PRIVACY_CHANGED`, `ACCOUNT_BLOCKED`), `timestamp`, `ipHash`.
