# 📊 Guia Completo - Como Usar o Grafana

## 🚀 Acesso Inicial

1. **Abra o Grafana no navegador:**
   ```
   http://localhost:3000
   ```

2. **Login:**
   - **Usuário**: `admin`
   - **Senha**: `admin`
   - (Na primeira vez, ele pede para trocar a senha - você pode pular)

## 🎯 Passo a Passo - Primeira Vez

### 1. Verificar Datasource (Fonte de Dados)

1. Clique em **⚙️ Configuration** (ícone de engrenagem, menu esquerdo)
2. Clique em **Data Sources**
3. Você deve ver **Prometheus** listado
4. Clique em **Prometheus**
5. Clique no botão **"Save & Test"** (no final da página)
6. Deve aparecer: ✅ **"Data source is working"**

**Se não aparecer:**
- Verifique se o Prometheus está rodando: http://localhost:9090
- Verifique a URL: deve ser `http://localhost:9090`

### 2. Explorar Métricas (Explore)

1. Clique em **🔍 Explore** (ícone de bússola, menu esquerdo)
2. No topo, selecione **Prometheus** como datasource
3. Digite uma query, por exemplo:
   ```promql
   order_processed_total
   ```
4. Clique em **Run query** (botão azul no topo direito)
5. Você verá os dados em formato de tabela ou gráfico

**Queries para testar:**
```promql
# Total de pedidos processados
order_processed_total

# Falhas por motivo
order_processed_total

# Estado do Circuit Breaker
resilience4j_circuitbreaker_state{name="order-service"}

# Taxa de requisições HTTP
rate(http_server_requests_seconds_count[5m])
```

### 3. Ver o Dashboard Pré-configurado

1. Clique em **📊 Dashboards** (menu esquerdo)
2. Clique em **Browse**
3. Procure por: **"Order Service - Resilience4j & Application Metrics"**
4. Clique para abrir

**Se o dashboard não aparecer:**
- Vá em **Dashboards** → **Import**
- Faça upload do arquivo: `monitoring/grafana/dashboards/order-service-dashboard.json`

## 📈 O Que Você Verá no Dashboard

O dashboard tem vários painéis:

### 1. **HTTP Request Rate**
- Mostra quantas requisições por segundo
- Gráfico de linha mostrando a taxa ao longo do tempo

### 2. **HTTP Request Latency (p95)**
- Tempo de resposta (95% das requisições)
- Mostra se está lento

### 3. **Circuit Breaker State**
- **CLOSED** (verde) = Normal
- **OPEN** (vermelho) = Circuito aberto (serviço com problemas)
- **HALF_OPEN** (amarelo) = Testando se voltou ao normal

### 4. **Circuit Breaker Calls**
- Gráfico mostrando chamadas bem-sucedidas vs falhas

### 5. **Retry Statistics**
- Mostra quantas vezes o sistema tentou novamente
- `successful_with_retry` = Sucesso após tentar novamente
- `failed_with_retry` = Falhou mesmo após tentar

### 6. **Bulkhead Available Calls**
- Mostra quantas chamadas simultâneas estão disponíveis
- Se chegar a 0, o sistema está sobrecarregado

### 7. **Rate Limiter Calls**
- Mostra chamadas que foram limitadas (muitas requisições)

### 8. **JVM Memory**
- Uso de memória da aplicação Java

## 🎨 Como Criar Seu Próprio Painel

### Criar um Novo Dashboard

1. Clique em **Dashboards** → **New Dashboard** (ou **+** → **Dashboard**)
2. Clique em **Add visualization** (ou **Add** → **Visualization**)
3. Selecione **Prometheus** como datasource
4. Digite sua query, por exemplo:
   ```promql
   order_processed_total
   ```
5. Clique em **Run query**
6. Ajuste o tipo de visualização (gráfico, tabela, estatística, etc.)
7. Clique em **Apply** (canto superior direito)
8. Clique em **Save dashboard** (ícone de disco, topo)

### Tipos de Visualização

- **Time series**: Gráfico de linha ao longo do tempo (padrão)
- **Stat**: Número grande (ex: total de pedidos)
- **Table**: Tabela com dados
- **Bar chart**: Gráfico de barras
- **Gauge**: Medidor circular

### Exemplo: Criar Painel de Pedidos Processados

1. **New Dashboard** → **Add visualization**
2. Query:
   ```promql
   order_processed_total
   ```
3. Mude para **Stat** (no painel direito, em "Visualization")
4. **Panel title**: "Total Orders Processed"
5. **Apply** → **Save dashboard**

## 🔍 Explorar Métricas (Explore)

O **Explore** é ótimo para testar queries antes de criar painéis:

1. Clique em **🔍 Explore**
2. Selecione **Prometheus**
3. Digite uma query
4. Escolha o formato:
   - **Table**: Ver dados em tabela
   - **Time series**: Ver como gráfico
5. Ajuste o intervalo de tempo (canto superior direito):
   - **Last 15 minutes**
   - **Last 1 hour**
   - **Last 6 hours**
   - Ou escolha um range customizado

## 📊 Queries Úteis para Você

### Métricas de Negócio

```promql
# Total de pedidos processados
order_processed_total

# Pedidos bem-sucedidos
order_successful_total

# Falhas por motivo
order_failed_total by (reason)

# Taxa de sucesso (%)
rate(order_successful_total[5m]) / rate(order_processed_total[5m]) * 100

# Pedidos por código postal
order_by_postal_code_total
```

### Métricas de Resilência

```promql
# Estado do Circuit Breaker
resilience4j_circuitbreaker_state{name="order-service"}

# Taxa de retry
rate(resilience4j_retry_calls_total{name="order-service"}[5m])

# Chamadas do Circuit Breaker
rate(resilience4j_circuitbreaker_calls_seconds_count{name="order-service"}[5m])
```

### Métricas HTTP

```promql
# Taxa de requisições
rate(http_server_requests_seconds_count[5m])

# Latência média
rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m])

# Latência p95
http_server_requests_seconds{quantile="0.95"}
```

## ⚙️ Configurações Importantes

### Intervalo de Tempo

No canto superior direito, você pode escolher:
- **Last 15 minutes** (padrão)
- **Last 1 hour**
- **Last 6 hours**
- **Last 24 hours**
- Ou um range customizado

### Atualização Automática

No dashboard, clique no ícone de **refresh** (🔄) no topo para escolher:
- **Off**: Não atualiza automaticamente
- **5s**: Atualiza a cada 5 segundos
- **10s**: Atualiza a cada 10 segundos (recomendado)
- **30s**: Atualiza a cada 30 segundos

## 🎯 Casos de Uso Práticos

### 1. Verificar se o Sistema Está Funcionando

1. Abra o dashboard
2. Verifique **Circuit Breaker State** = **CLOSED** (verde)
3. Verifique **HTTP Request Rate** > 0 (há requisições)
4. Verifique **Retry Statistics** (não deve ter muitas falhas)

### 2. Investigar Problemas

1. Se **Circuit Breaker** = **OPEN** (vermelho):
   - O serviço externo está com problemas
   - Veja **Circuit Breaker Calls** para detalhes

2. Se **Retry Statistics** mostra muitas falhas:
   - Veja **order_failed_total by (reason)** para ver o motivo

3. Se **Latency** está alto:
   - Veja **HTTP Request Latency (p95)**
   - Pode indicar sobrecarga

### 3. Monitorar Performance

1. **HTTP Request Rate**: Quantas requisições por segundo
2. **Latency**: Tempo de resposta
3. **JVM Memory**: Uso de memória (se estiver alto, pode precisar ajustar)

## 🆘 Troubleshooting

### "No data" nos painéis

**Causas:**
1. Não há métricas ainda (faça algumas requisições à API)
2. Intervalo de tempo está errado (mude para "Last 15 minutes")
3. Query está incorreta

**Solução:**
1. Faça algumas requisições:
   ```powershell
   curl "http://localhost:8080/orders?orderNumber=ORDER-1"
   ```
2. Aguarde 10-15 segundos
3. Atualize o dashboard (F5 ou botão refresh)

### Datasource não funciona

1. Vá em **Configuration** → **Data Sources**
2. Clique em **Prometheus**
3. Clique em **"Save & Test"**
4. Se falhar, verifique se Prometheus está rodando: http://localhost:9090

### Dashboard não aparece

1. Vá em **Dashboards** → **Import**
2. Faça upload de: `monitoring/grafana/dashboards/order-service-dashboard.json`

## 📚 Próximos Passos

1. **Explore as métricas**: Use o **Explore** para testar queries
2. **Crie painéis customizados**: Adicione métricas que você precisa
3. **Configure alertas**: Avisos quando algo der errado (opcional)
4. **Exporte dashboards**: Compartilhe com a equipe

## 🎓 Dicas

- **Use o Explore primeiro**: Teste queries antes de criar painéis
- **Ajuste o intervalo de tempo**: Dados recentes são mais úteis
- **Ative auto-refresh**: Para ver dados em tempo real
- **Salve dashboards**: Não perca suas configurações
- **Use variáveis**: Para tornar dashboards mais flexíveis (avançado)

---

**Agora você está pronto para usar o Grafana! 🚀**

Comece explorando o dashboard pré-configurado e depois crie seus próprios painéis conforme necessário.

