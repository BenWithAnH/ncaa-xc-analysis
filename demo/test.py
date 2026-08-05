import urllib.request
import json

url = 'http://localhost:8080/api/race/rate'
payload = {'meetUrl': 'https://tfrrs.org/results/xc/27292/NCAA_Division_III_Cross_Country_Championships'}
data = json.dumps(payload).encode('utf-8')
req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})

try:
    with urllib.request.urlopen(req) as response:
        print(f"Status Code: {response.status}")
        print(f"Response: {response.read().decode('utf-8')[:500]}")
except Exception as e:
    print(f"Error: {e}")
