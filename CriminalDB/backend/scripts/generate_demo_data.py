import urllib.request
import json
import random
import uuid
import mimetypes
import sys
import os
from datetime import date, timedelta

API_BASE = os.getenv("CRIMINALDB_API_BASE", "http://localhost:10000/api").rstrip("/")
ADMIN_USERNAME = os.getenv("CRIMINALDB_USERNAME") or os.getenv("APP_ADMIN_USERNAME")
ADMIN_PASSWORD = os.getenv("CRIMINALDB_PASSWORD") or os.getenv("APP_ADMIN_PASSWORD")
WIPE_EXISTING = os.getenv("CRIMINALDB_WIPE_EXISTING", "false").lower() == "true"

# --- Fictional Dataset Source Arrays ---
first_names_male = ["James", "John", "Robert", "Michael", "William", "David", "Richard", "Joseph", "Thomas", "Charles", "Christopher", "Daniel", "Matthew", "Anthony", "Mark", "Donald", "Steven", "Paul", "Andrew", "Joshua", "Kenneth", "Kevin", "Brian", "George", "Edward", "Ronald", "Timothy", "Jason", "Jeffrey", "Ryan", "Jacob", "Gary", "Nicholas", "Eric", "Jonathan", "Stephen", "Larry", "Justin", "Scott", "Brandon", "Benjamin", "Samuel", "Gregory", "Frank", "Alexander", "Raymond", "Patrick", "Jack", "Dennis", "Jerry"]
first_names_female = ["Mary", "Patricia", "Jennifer", "Linda", "Elizabeth", "Barbara", "Susan", "Jessica", "Sarah", "Karen", "Lisa", "Nancy", "Betty", "Sandra", "Margaret", "Ashley", "Kimberly", "Emily", "Donna", "Michelle", "Carol", "Amanda", "Dorothy", "Melissa", "Deborah", "Stephanie", "Rebecca", "Sharon", "Laura", "Cynthia", "Kathleen", "Amy", "Shirley", "Angela", "Helen", "Anna", "Brenda", "Pamela", "Nicole", "Emma", "Samantha", "Katherine", "Christine", "Debra", "Rachel", "Carolyn", "Janet", "Maria", "Heather", "Diane"]
last_names = ["Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson", "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson", "Walker", "Young", "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill", "Flores", "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera", "Campbell", "Mitchell", "Carter", "Roberts"]

nationalities = ["American", "Canadian", "British", "Australian", "German", "French", "Italian", "Spanish", "Mexican", "Brazilian", "Indian", "Japanese", "Chinese", "South African"]
blood_groups = ["A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"]
occupations = ["Unemployed", "Laborer", "Truck Driver", "Mechanic", "Construction Worker", "Sales Associate", "Security Guard", "Electrician", "Plumber", "Warehouse Worker", "Freelancer", "Barista", "Technician", "None"]

cities_states = [
    ("New York", "NY", "10001"), ("Los Angeles", "CA", "90001"), ("Chicago", "IL", "60601"),
    ("Houston", "TX", "77001"), ("Phoenix", "AZ", "85001"), ("Philadelphia", "PA", "19101"),
    ("San Antonio", "TX", "78201"), ("San Diego", "CA", "92101"), ("Dallas", "TX", "75201"),
    ("San Jose", "CA", "95101"), ("Austin", "TX", "78701"), ("Jacksonville", "FL", "32201"),
    ("Fort Worth", "TX", "76101"), ("Columbus", "OH", "43201"), ("Charlotte", "NC", "28201"),
    ("San Francisco", "CA", "94101"), ("Indianapolis", "IN", "46201"), ("Seattle", "WA", "98101"),
    ("Denver", "CO", "80201"), ("Washington", "DC", "20001")
]

crime_categories = ["Theft", "Burglary", "Robbery", "Assault", "Murder", "Cyber Crime", "Fraud", "Drug Trafficking", "Human Trafficking", "Kidnapping", "Smuggling", "Vehicle Theft", "Illegal Possession", "Financial Crime", "Vandalism"]
police_stations = ["Central Precinct", "Downtown Station", "Metro Police Dept", "Northside Substation", "East District Precinct", "South Valley Station", "Harbor Patrol Division", "Westside Command"]

crime_details = {
  "Theft": {
    "desc": "Grand theft of retail merchandise and high-value electronics.",
    "notes": "Targeted jewelry and electronics. Usually bypasses simple latch locks using specialized tension picks."
  },
  "Burglary": {
    "desc": "Forced entry into residential properties during night hours.",
    "notes": "Targeted jewelry and electronics. Usually bypasses simple latch locks using specialized tension picks."
  },
  "Robbery": {
    "desc": "Armed robbery of convenience store using replica firearms.",
    "notes": "Subdued counter clerk with threat of force. Fled scene on a black unregistered motorbike."
  },
  "Assault": {
    "desc": "Physical altercation resulting in grievous bodily harm.",
    "notes": "Involved in a bar brawl. Assaulted multiple security personnel using improvised glass weapons."
  },
  "Murder": {
    "desc": "First-degree murder involving deliberate criminal intent.",
    "notes": "Highly volatile profile. Investigated for links to local organized crime syndicates."
  },
  "Cyber Crime": {
    "desc": "Unlawful network intrusion and distributed denial of service attack.",
    "notes": "Compromised local enterprise network systems using stolen credential configurations."
  },
  "Fraud": {
    "desc": "Identity theft and unauthorized credit line withdrawals.",
    "notes": "Forged credit documents and withdrew funds across state bank networks."
  },
  "Drug Trafficking": {
    "desc": "Smuggling and local distribution of prohibited contraband.",
    "notes": "Discovered carrying bulk packages inside hidden trunk cavities of personal vehicles."
  },
  "Human Trafficking": {
    "desc": "Illegal border transport and exploitation of labor assets.",
    "notes": "Associated with cross-border transport networks. Held key documents under falsified aliases."
  },
  "Kidnapping": {
    "desc": "Abduction and holding for unlawful ransom negotiations.",
    "notes": "Held victim inside a remote cabin site. Arrested during coordinated tactical raid."
  },
  "Smuggling": {
    "desc": "Illegal transport of untaxed luxury goods across port borders.",
    "notes": "Concealed microchips and high-end tech goods inside heavy cargo machine containers."
  },
  "Vehicle Theft": {
    "desc": "Unlawful grand theft auto of luxury sports vehicles.",
    "notes": "Uses signal amplifiers to clone keyless entry frequencies of modern sedans."
  },
  "Illegal Possession": {
    "desc": "Possession of unregistered custom automatic weapons.",
    "notes": "Discovered with modifications on standard tactical firearms inside residential safe lockers."
  },
  "Financial Crime": {
    "desc": "Corporate embezzlement and illegal offshore laundering.",
    "notes": "Transferred funds through corporate shells to shell bank structures in Panama."
  },
  "Vandalism": {
    "desc": "Property defacement and structural damage to public transit nodes.",
    "notes": "Repeated offenses on metropolitan trains. Associated with anti-establishment street networks."
  }
}

statuses = ["Wanted", "Arrested", "Active", "Bailed", "Closed"]

# --- Helper Methods ---
def random_date(start_year, end_year):
    start_date = date(start_year, 1, 1)
    end_date = date(end_year, 12, 31)
    time_between_dates = end_date - start_date
    days_between_dates = time_between_dates.days
    random_number_of_days = random.randrange(days_between_dates)
    return (start_date + timedelta(days=random_number_of_days)).isoformat()

def encode_multipart_formdata(fields, files):
    boundary = b'----WebKitFormBoundary' + uuid.uuid4().hex.encode()
    lines = []
    
    for name, value in fields.items():
        lines.append(b'--' + boundary)
        lines.append(f'Content-Disposition: form-data; name="{name}"'.encode())
        lines.append(b'')
        lines.append(str(value).encode())
        
    for name, (filename, content) in files.items():
        lines.append(b'--' + boundary)
        lines.append(f'Content-Disposition: form-data; name="{name}"; filename="{filename}"'.encode())
        mimetype = mimetypes.guess_type(filename)[0] or 'application/octet-stream'
        lines.append(f'Content-Type: {mimetype}'.encode())
        lines.append(b'')
        lines.append(content)
        
    lines.append(b'--' + boundary + b'--')
    lines.append(b'')
    
    body = b'\r\n'.join(lines)
    content_type = f'multipart/form-data; boundary={boundary.decode()}'
    return content_type, body

def main():
    if not ADMIN_USERNAME or not ADMIN_PASSWORD:
        print("Set CRIMINALDB_USERNAME and CRIMINALDB_PASSWORD before running this script.", file=sys.stderr)
        sys.exit(2)

    print("[1/3] Authenticating as ADMIN on Node Registry...", flush=True)
    
    # 1. Authenticate with server
    login_data = json.dumps({"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD}).encode()
    req = urllib.request.Request(
        f"{API_BASE}/auth/login",
        data=login_data,
        headers={"Content-Type": "application/json"}
    )
    
    try:
        with urllib.request.urlopen(req) as res:
            auth_response = json.loads(res.read().decode())
            token = auth_response["token"]
            print("Authentication validated. Node token obtained.", flush=True)
    except Exception as e:
        print(f"FAILED TO AUTHENTICATE. Make sure Spring Boot is running! Details: {e}", file=sys.stderr, flush=True)
        sys.exit(1)

    if WIPE_EXISTING:
      print("Wiping existing records for clean slate...")
      try:
        req_list = urllib.request.Request(f"{API_BASE}/persons", headers={"Authorization": f"Bearer {token}"})
        with urllib.request.urlopen(req_list) as res_list:
          current_persons = json.loads(res_list.read().decode())
        for p in current_persons:
          pid = p["id"]
          req_del = urllib.request.Request(f"{API_BASE}/persons/{pid}", headers={"Authorization": f"Bearer {token}"}, method="DELETE")
          try:
            urllib.request.urlopen(req_del)
          except Exception as e:
            print(f"Failed to delete existing {pid}: {e}", flush=True)
        print("Wipe completed.", flush=True)
      except Exception as e:
        print(f"Wipe failed: {e}", flush=True)
    else:
      print("Keeping existing records. Set CRIMINALDB_WIPE_EXISTING=true to delete them first.", flush=True)

    print("[2/3] Registering 20 classified profiles...", flush=True)

    used_photos = set()
    
    for i in range(20):
        # Determine Gender (Alternate 10 Male, 10 Female)
        gender = "Male" if i < 10 else "Female"
        photo_idx = (i % 10) + 1  # 1 to 10
        
        # Download unique photo from RandomUser me
        gender_path = "men" if gender == "Male" else "women"
        photo_url = f"https://randomuser.me/api/portraits/{gender_path}/{photo_idx}.jpg"
        
        try:
            # Set user agent to bypass blockers
            photo_req = urllib.request.Request(photo_url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(photo_req) as photo_res:
                photo_bytes = photo_res.read()
        except Exception as e:
            print(f"Skipping index {i}: Failed to download photo from RandomUser. {e}", flush=True)
            continue
            
        # Select Names
        fn = random.choice(first_names_male) if gender == "Male" else random.choice(first_names_female)
        ln = random.choice(last_names)
        full_name = f"{fn} {ln}"
        
        # Unique parameters
        criminal_id = f"CR-{1000 + i}"
        fir_number = f"FIR-{20000 + i}"
        case_number = f"CASE-{50000 + i}"
        phone = f"+1-{random.randint(200,999)}-{random.randint(200,999)}-{random.randint(1000,9999)}"
        email = f"{fn.lower()}.{ln.lower()}@node.registry"
        
        # Generate biography/notes inside otherDetails
        dob = random_date(1970, 2004)
        arrest_date = random_date(2021, 2026)
        
        height = random.randint(155, 195)
        weight = random.randint(55, 110)
        blood = random.choice(blood_groups)
        nat = random.choice(nationalities)
        job = random.choice(occupations)
        city_info = random.choice(cities_states)
        
        crime_cat = random.choice(crime_categories)
        crime_info = crime_details[crime_cat]
        status = random.choice(statuses)
        police_station = random.choice(police_stations)
        
        # Compile all the extra data in a structured, classified dossier text block
        other_details_formatted = f"""--- CLASSIFIED DOSSIER DETAILS ---
Case Number: {case_number}
Nationality: {nat} | Blood Group: {blood}
Height: {height} cm | Weight: {weight} kg
Email: {email}
Occupation: {job}
Fingerprint ID: FP-{random.randint(100000,999999)} | DNA Sample ID: DNA-{random.randint(100000,999999)}
Aliases: The {random.choice(["Ghost", "Broker", "Specter", "Wraith", "Spider", "Jackal", "Fox"])}
Risk Level: {random.choice(["Low", "Medium", "High", "Critical"])}
Last Known Location: Sector {random.randint(1,12)} Subgrid
Previous History: Investigated for regional offenses. Registered status details below."""

        fields = {
            "fullName": full_name,
            "dateOfBirth": dob,
            "gender": gender,
            "address": f"{random.randint(100,9999)} {random.choice(['Main St', 'Broadway', 'Oak Ave', 'Pine Rd', 'Elm St'])}, {city_info[0]}",
            "phoneNumber": phone,
            "otherDetails": other_details_formatted,
            "criminalId": criminal_id,
            "crimeCategory": crime_cat,
            "firNumber": fir_number,
            "policeStation": police_station,
            "currentStatus": status,
            "arrestDate": arrest_date,
            "crimeDescription": crime_info["desc"]
        }
        
        files = {
            "photo": (f"{criminal_id}.jpg", photo_bytes)
        }
        
        # Build multipart request
        content_type, body = encode_multipart_formdata(fields, files)
        
        post_req = urllib.request.Request(
            f"{API_BASE}/persons",
            data=body,
            headers={
                "Content-Type": content_type,
                "Authorization": f"Bearer {token}"
            }
        )
        
        try:
            with urllib.request.urlopen(post_req) as post_res:
                post_data = json.loads(post_res.read().decode())
                print(f"[{i+1}/20] Registered: {full_name} ({criminal_id}) [Score Matching Active]", flush=True)
        except Exception as e:
            # Check if there's error message
            print(f"[{i+1}/20] FAILED to register: {full_name}. {e}", flush=True)

    print("[3/3] Demo dataset population completed successfully.", flush=True)

if __name__ == "__main__":
    main()
