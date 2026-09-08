"""Add DM metadata to an existing SQLite database without resetting any data."""
import argparse
from contextlib import closing
from datetime import datetime, timezone
from pathlib import Path
import sqlite3


def migrate(database):
    path = Path(database).resolve(strict=True)
    backup = path.with_name(f"{path.name}.before-dm-{datetime.now(timezone.utc):%Y%m%d%H%M%S%f}.bak")
    with closing(sqlite3.connect(path)) as connection:
        # Use SQLite backup so committed WAL data is included.
        with closing(sqlite3.connect(backup)) as destination:
            connection.backup(destination)
        with connection:
            columns = {row[1] for row in connection.execute('PRAGMA table_info("groups")')}
            if not columns:
                raise RuntimeError("No groups table found; check the database path")
            if "dm_key" not in columns:
                connection.execute('ALTER TABLE "groups" ADD COLUMN dm_key VARCHAR(255)')
            connection.execute('CREATE UNIQUE INDEX IF NOT EXISTS ux_groups_dm_key ON "groups" (dm_key)')
            connection.execute('CREATE TABLE IF NOT EXISTS dm_read_receipt (id VARCHAR(255) PRIMARY KEY, read_at VARCHAR(255))')
    print(f"DM migration complete. Backup: {backup}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("database", help="Path to the existing SQLite database (stop Spring first)")
    migrate(parser.parse_args().database)
