(async () => {
  const ws = new WebSocket('ws://localhost:5555/api/ws');

  let currentSource = 'System';
  const logBuffer = {
    System: [],
    Git: [],
    Gradle: [],
    Server: [],
  };

  const logs = document.querySelector('pre#logs');
  function log(source, content) {
    logBuffer[source].push(content);
    if(currentSource === source) {
      logs.textContent += content;
    }
  }

  function showSource(source) {
    document.querySelector(`button#source-${currentSource.toLowerCase()}`).disabled = false;
    document.querySelector(`button#source-${source.toLowerCase()}`).disabled = true;

    currentSource = source;
    logs.textContent = logBuffer[source].join('');
  }

  function send(message) {
    const content = JSON.stringify(message);
    ws.send(content);
  }

  const clearLogs = document.querySelector('button#clear-logs');

  clearLogs.addEventListener('click', () => {
    logBuffer[currentSource] = [];
    logs.textContent = '';

    if(currentSource !== 'System') {
      send({ _: 'ClearLogs', source: currentSource });
    }
  });

  const sourceSystem = document.querySelector('button#source-system');
  const sourceGit = document.querySelector('button#source-git');
  const sourceGradle = document.querySelector('button#source-gradle');
  const sourceServer = document.querySelector('button#source-server');

  sourceSystem.disabled = true;
  sourceSystem.addEventListener('click', () => showSource('System'));
  sourceGit.addEventListener('click', () => showSource('Git'));
  sourceGradle.addEventListener('click', () => showSource('Gradle'));
  sourceServer.addEventListener('click', () => showSource('Server'));

  const serverState = document.querySelector('span#server-state');
  const startServer = document.querySelector('button#start-server');
  const stopServer = document.querySelector('button#stop-server');

  startServer.disabled = true;
  stopServer.disabled = true;

  startServer.addEventListener('click', () => {
    send({ _: 'ServerAction', action: 'Start' });
  });

  stopServer.addEventListener('click', () => {
    send({ _: 'ServerAction', action: 'Stop' });
  });

  const gitState = document.querySelector('span#git-state');
  const updateGit = document.querySelector('button#update-git');

  updateGit.disabled = true;
  updateGit.addEventListener('click', () => {
    send({ _: 'VcsUpdate' });
  });

  const gradleState = document.querySelector('span#gradle-state');
  const buildGradle = document.querySelector('button#build-gradle');

  buildGradle.disabled = true;
  buildGradle.addEventListener('click', () => {
    send({ _: 'GradleBuild' });
  });

  ws.addEventListener('open', () => {
    log('System', `>>> Connected to the server\n`);
  });

  ws.addEventListener('close', (event) => {
    log('System', `>>> Disconnected from the server (code: ${event.code})\n`);
  });

  ws.addEventListener('error', (event) => {
    log('System', `>>> Error: ${event.message}\n`);
  });

  ws.addEventListener('message', (event) => {
    const data = JSON.parse(event.data);
    if(data._ === 'Log') {
      log(data.source, data.content);
    } else if(data._ === 'ServerState') {
      serverState.textContent = data.state;
      startServer.disabled = data.state !== 'Stopped';
      stopServer.disabled = data.state !== 'Started';
    } else if(data._ === 'VcsState') {
      gitState.textContent = data.state;
      updateGit.disabled = data.state !== 'Idle';
    } else if(data._ === 'GradleState') {
      gradleState.textContent = data.state;
      buildGradle.disabled = data.state !== 'Idle';
    }
  });
})();
