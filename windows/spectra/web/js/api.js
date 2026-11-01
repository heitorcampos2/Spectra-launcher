export class ApiError extends Error {
  constructor(msg, status, data) { super(msg); this.status = status; this.data = data; }
}

async function req(method, path, body) {
  const opts = { method, headers: {} };
  if (body !== undefined) {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(body);
    // garante que o ponto do vídeo seja salvo mesmo ao fechar a aba
    if (method === 'POST' && opts.body.length < 4000) opts.keepalive = true;
  }
  let res;
  try {
    res = await fetch(path, opts);
  } catch {
    throw new ApiError('Sem conexão com o Spectra. Abra o Spectra de novo.', 0);
  }
  let data = null;
  try { data = await res.json(); } catch { data = null; }
  if (!res.ok) {
    if (res.status === 401 && data && data.auth) window.dispatchEvent(new CustomEvent('sintonia:auth'));
    throw new ApiError((data && data.error) || `Erro ${res.status}`, res.status, data);
  }
  return data;
}

export const api = {
  get: (p) => req('GET', p),
  post: (p, b = {}) => req('POST', p, b),
  del: (p) => req('DELETE', p),
};
