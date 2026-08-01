import os
import random
import subprocess
import sys
import tempfile
import urllib.request
from datetime import date, timedelta

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
UPLOADS_DIR = os.path.join(ROOT_DIR, 'uploads')
SCHEMA_SQL = os.path.join(ROOT_DIR, '..', 'database', 'schema.sql')
DEFAULT_ROW_COUNT = 20

first_names_male = [
    "James", "John", "Robert", "Michael", "William", "David", "Richard", "Joseph",
    "Thomas", "Charles", "Christopher", "Daniel", "Matthew", "Anthony", "Mark", "Donald",
    "Steven", "Paul", "Andrew", "Joshua", "Kenneth", "Kevin", "Brian", "George",
    "Edward", "Ronald", "Timothy", "Jason", "Jeffrey", "Ryan", "Jacob", "Gary",
    "Nicholas", "Eric", "Jonathan", "Stephen", "Larry", "Justin", "Scott", "Brandon",
    "Benjamin", "Samuel", "Gregory", "Frank", "Alexander", "Raymond", "Patrick", "Jack",
    "Dennis", "Jerry"
]
first_names_female = [
    "Mary", "Patricia", "Jennifer", "Linda", "Elizabeth", "Barbara", "Susan", "Jessica",
    "Sarah", "Karen", "Lisa", "Nancy", "Betty", "Sandra", "Margaret", "Ashley",
    "Kimberly", "Emily", "Donna", "Michelle", "Carol", "Amanda", "Dorothy", "Melissa",
    "Deborah", "Stephanie", "Rebecca", "Sharon", "Laura", "Cynthia", "Kathleen", "Amy",
    "Shirley", "Angela", "Helen", "Anna", "Brenda", "Pamela", "Nicole", "Emma",
    "Samantha", "Katherine", "Christine", "Debra", "Rachel", "Carolyn", "Janet", "Maria",
    "Heather", "Diane"
]
last_names = [
    "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
    "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
    "Thomas", "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson",
    "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson", "Walker",
    "Young", "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill",
    "Flores", "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera", "Campbell",
    "Mitchell", "Carter", "Roberts"
]

crime_categories = [
    "Theft", "Burglary", "Robbery", "Assault", "Murder", "Cyber Crime", "Fraud",
    "Drug Trafficking", "Human Trafficking", "Kidnapping", "Smuggling", "Vehicle Theft",
    "Illegal Possession", "Financial Crime", "Vandalism"
]
police_stations = [
    "Central Precinct", "Downtown Station", "Metro Police Dept", "Northside Substation",
    "East District Precinct", "South Valley Station", "Harbor Patrol Division", "Westside Command"
]
statuses = ["Wanted", "Arrested", "Active", "Bailed", "Closed"]


def random_date(start_year, end_year):
    start = date(start_year, 1, 1)
    end = date(end_year, 12, 31)
    delta = (end - start).days
    return (start + timedelta(days=random.randrange(delta))).isoformat()


def escape_sql(value: str) -> str:
    return value.replace("'", "''")


def find_mysql_client() -> str:
    for name in ['mysql', '/usr/bin/mysql', '/usr/local/bin/mysql']:
        if os.path.isfile(name) and os.access(name, os.X_OK):
            return name
    raise FileNotFoundError('MySQL client not found. Install mysql-client or add mysql to PATH.')


def load_db_credentials() -> str:
    try:
        output = subprocess.check_output(['sudo', '-n', 'cat', '/etc/mysql/debian.cnf'], text=True)
        tmp = tempfile.NamedTemporaryFile(delete=False, mode='w', prefix='mysqlcreds_', suffix='.cnf')
        tmp.write(output)
        tmp.close()
        return tmp.name
    except subprocess.CalledProcessError:
        env_user = os.environ.get('DB_USER')
        env_password = os.environ.get('DB_PASSWORD')
        if env_user and env_password:
            tmp = tempfile.NamedTemporaryFile(delete=False, mode='w', prefix='mysqlcreds_', suffix='.cnf')
            tmp.write('[client]\n')
            tmp.write(f'user={env_user}\n')
            tmp.write(f'password={env_password}\n')
            tmp.close()
            return tmp.name
        raise RuntimeError(
            'Unable to read /etc/mysql/debian.cnf via sudo and DB_USER/DB_PASSWORD are not set. '
            'Set environment variables or run this script with sudo privileges.'
        )


def run_mysql_query(defaults_file: str, query: str) -> str:
    mysql = find_mysql_client()
    result = subprocess.run([mysql, f'--defaults-file={defaults_file}', '-D', 'facedb', '-e', query],
                            capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f'MySQL query failed: {result.stderr.strip()}')
    return result.stdout.strip()


def init_schema(defaults_file: str):
    if not os.path.exists(SCHEMA_SQL):
        raise FileNotFoundError(f'Schema file not found: {SCHEMA_SQL}')
    mysql = find_mysql_client()
    result = subprocess.run([mysql, f'--defaults-file={defaults_file}'],
                            stdin=open(SCHEMA_SQL, 'r'), capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f'Failed to initialize schema: {result.stderr.strip()}')


def regenerate(count: int = DEFAULT_ROW_COUNT):
    os.makedirs(UPLOADS_DIR, exist_ok=True)
    defaults_file = load_db_credentials()
    try:
        print('Initializing database schema...')
        init_schema(defaults_file)
        print('Clearing existing persons...')
        run_mysql_query(defaults_file, 'TRUNCATE TABLE persons;')
    except RuntimeError as e:
        print(f'ERROR: {e}', file=sys.stderr)
        return

    commands = []
    for i in range(count):
        gender = 'Male' if i < count / 2 else 'Female'
        idx = i % 10
        fn = random.choice(first_names_male) if gender == 'Male' else random.choice(first_names_female)
        ln = random.choice(last_names)
        full_name = f"{fn} {ln}"
        dob = random_date(1970, 2004)
        address = f"{random.randint(100, 9999)} {random.choice(['Main St', 'Broadway', 'Oak Ave', 'Pine Rd', 'Elm St'])}, Sector {random.randint(1, 20)}"
        other_details = (
            f"Last seen near {random.choice(['the waterfront', 'industrial park', 'downtown plaza', 'transit terminal'])}. "
            f"{random.choice(['Known to carry a concealed weapon.', 'Linked to a prior fraud investigation.', 'Has distinctive left jaw scar.', 'Is considered armed and dangerous.'])}"
        )
        photo_name = f"sample_{i+1}.jpg"
        photo_path = os.path.join(UPLOADS_DIR, photo_name)
        photo_rel = os.path.join('uploads', photo_name)
        gender_path = 'men' if gender == 'Male' else 'women'
        photo_url = f"https://randomuser.me/api/portraits/{gender_path}/{idx}.jpg"
        try:
            urllib.request.urlretrieve(photo_url, photo_path)
        except Exception:
            with open(photo_path, 'wb') as f:
                f.write(b'')

        embedding = ','.join(f"{random.random():.6f}" for _ in range(128))
        commands.append(
            "INSERT INTO persons (full_name, date_of_birth, address, other_details, photo_path, face_embedding, created_at, updated_at, created_by) VALUES ('{}','{}','{}','{}','{}','{}',NOW(),NOW(),'admin');".format(
                escape_sql(full_name), dob, escape_sql(address), escape_sql(other_details), escape_sql(photo_rel), escape_sql(embedding)
            )
        )

    script = '\n'.join(commands)
    print(f'Inserting {count} sample person records...')
    mysql = find_mysql_client()
    process = subprocess.run([mysql, f'--defaults-file={defaults_file}', '-D', 'facedb'],
                             input=script, text=True, capture_output=True)
    if process.returncode != 0:
        raise RuntimeError(f'MySQL insert failed: {process.stderr.strip()}')
    print(f'Finished creating {count} sample records. Photos saved in {UPLOADS_DIR}.')


if __name__ == '__main__':
    count = DEFAULT_ROW_COUNT
    if len(sys.argv) > 1:
        try:
            count = int(sys.argv[1])
        except ValueError:
            print('Usage: python regenerate_demo_data.py [count]', file=sys.stderr)
            sys.exit(1)
    regenerate(count)
