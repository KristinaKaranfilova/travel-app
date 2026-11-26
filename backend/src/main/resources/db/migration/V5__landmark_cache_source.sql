-- Од каде е записот во кешот за знаменитости.
-- 'wikidata' (најдобар квалитет) важи 30 дена, а резервниот 'wikipedia' само 1 ден,
-- за наскоро повторно да се проба Wikidata. Порано резервниот резултат воопшто не се кешираше,
-- па секое отворање на град каде Wikidata е бавна траеше десетици секунди.
ALTER TABLE landmark_cache ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'wikidata';
