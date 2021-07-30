const https = require('https');

https.get("https://data.iana.org/time-zones/data/zone1970.tab", response => {
  var data = '';
  response.on('data', chunk => data += chunk);
  response.on('end', () => {
    const names = ['UTC'];
    for (let line of data.split('\n')) {
      if (!line || line.indexOf('#') === 0) {
        continue;
      }
      const parts = line.split('\t');
      names.push(parts[2]);
    }
    names.sort();

    let code = `export default tznames = [
  '${names.join('\',\n  \'')}'
];
`;
    console.log(code);
  });
});
