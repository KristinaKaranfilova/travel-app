# Travel Planner (дипломска работа)

React + Spring Boot + PostgreSQL/pgvector + Spring AI.

## Стартување (локално)

1. **База** (Docker Desktop мора да е вклучен):
   ```bash
   docker compose up -d
   ```
   Базата е на порта **5433** (5432 е зафатена од локално инсталиран PostgreSQL).

2. **Тајни** (еднаш): копирај `backend/secrets.properties.example` во
   `backend/secrets.properties` и внеси:
   - `GEMINI_API_KEY`: клучот од https://aistudio.google.com/apikey
   - `JWT_SECRET`: долг случаен текст (најмалку 32 знаци) за потпишување на токените за најава
   Без нив backend-от не стартува.

3. **Backend** (http://localhost:8080):
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
   При старт Flyway ги извршува SQL миграциите од `src/main/resources/db/migration`.

4. **Frontend** (http://localhost:5173):
   ```bash
   cd frontend
   npm run dev
   ```
   Барањата кон `/api/...` Vite ги препраќа кон backend-от (`vite.config.js`).

5. **Извори за чет-ботот** (еднаш по град; градот прво мора да е избран во апликацијата):
   ```bash
   curl -X POST "http://localhost:8080/api/admin/ingest/1?title=Ohrid"
   ```
   `1` е id на градот во базата, `Ohrid` е насловот на англиската Wikivoyage страница.
   Поради лимитот на бесплатниот Gemini план (30.000 токени/минута) голем град трае 1-2 минути.

## Состојба
- [x] Фаза 1: поставување (база, backend, frontend, `/api/health`)
- [x] Фаза 2: градови и места (Nominatim + Overpass, кеш)
- [x] Фаза 3: алгоритам за план (k-means, рута, распоред)
- [x] Фаза 4: frontend за планот и мапа
- [x] Фаза 5: RAG чет-бот (Gemini + pgvector). Внесени: Охрид, Скопје, Рим, Париз, Барселона
- [x] Фаза 6: евалуација (резултати во `evaluation/results`, рачната оценка на `rag_answers.csv` останува)
- [x] Дополнително: регистрација и најава (Spring Security + JWT), „Мои патувања“,
      популарни дестинации и галерија со знаменитости (Wikidata, резерва Wikipedia)
- [x] Дополнително: клик на атракција дава краток опис (Wikipedia/Wikidata) и ја означува на мапата
