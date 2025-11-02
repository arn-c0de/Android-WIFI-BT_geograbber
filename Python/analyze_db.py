import sqlite3
import sys

db_path = r"C:\Users\644aa\Downloads\MainScan-31.08.25.db"

try:
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    
    # Get all tables
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table';")
    tables = cursor.fetchall()
    
    print("=" * 60)
    print("DATABASE STRUCTURE ANALYSIS")
    print("=" * 60)
    print(f"\nDatabase: {db_path}")
    print(f"\nTables found: {len(tables)}")
    print("-" * 60)
    
    for table in tables:
        table_name = table[0]
        print(f"\n### Table: {table_name}")
        
        # Get column info
        cursor.execute(f"PRAGMA table_info({table_name});")
        columns = cursor.fetchall()
        
        print(f"Columns ({len(columns)}):")
        for col in columns:
            col_id, col_name, col_type, not_null, default, pk = col
            print(f"  - {col_name} ({col_type}){' [PRIMARY KEY]' if pk else ''}{' [NOT NULL]' if not_null else ''}")
        
        # Get row count
        cursor.execute(f"SELECT COUNT(*) FROM {table_name};")
        count = cursor.fetchone()[0]
        print(f"\nRow count: {count}")
        
        # Show sample data (first 3 rows)
        if count > 0:
            cursor.execute(f"SELECT * FROM {table_name} LIMIT 3;")
            sample_rows = cursor.fetchall()
            print(f"\nSample data (first {min(3, count)} rows):")
            for i, row in enumerate(sample_rows, 1):
                print(f"\n  Row {i}:")
                for col_idx, col_info in enumerate(columns):
                    col_name = col_info[1]
                    value = row[col_idx]
                    # Truncate long values
                    if isinstance(value, str) and len(value) > 50:
                        value = value[:50] + "..."
                    print(f"    {col_name}: {value}")
        
        print("-" * 60)
    
    conn.close()
    print("\nAnalysis complete!")
    
except Exception as e:
    print(f"Error: {e}")
    sys.exit(1)
