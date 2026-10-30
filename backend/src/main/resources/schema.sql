CREATE TABLE IF NOT EXISTS drama (
  id VARCHAR(64) PRIMARY KEY,
  title VARCHAR(160) NOT NULL,
  category VARCHAR(40) NOT NULL,
  tags VARCHAR(200) NOT NULL,
  synopsis VARCHAR(2000) NOT NULL,
  poster VARCHAR(400) NOT NULL,
  backdrop VARCHAR(400) NOT NULL,
  release_year INT NOT NULL,
  duration INT NOT NULL,
  episode_count INT NOT NULL,
  sort_order INT NOT NULL,
  credit VARCHAR(300) NOT NULL,
  license_name VARCHAR(160) NOT NULL,
  source_page VARCHAR(600) NOT NULL
);
CREATE TABLE IF NOT EXISTS episode (
  id VARCHAR(80) PRIMARY KEY,
  drama_id VARCHAR(64) NOT NULL,
  episode_number INT NOT NULL,
  title VARCHAR(200) NOT NULL,
  duration INT NOT NULL,
  video_url VARCHAR(1200) NOT NULL,
  fallback_url VARCHAR(1200),
  UNIQUE (drama_id, episode_number),
  FOREIGN KEY (drama_id) REFERENCES drama(id)
);
