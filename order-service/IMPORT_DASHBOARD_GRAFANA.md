# Como Importar o Dashboard no Grafana

## Método 1: Importação Manual (Recomendado)

### Passo a Passo:

1. **Abra o Grafana:**
   ```
   http://localhost:3000
   ```

2. **Vá para Import:**
   - Clique em **📊 Dashboards** (menu esquerdo)
   - Clique em **Import** (ou **+** → **Import**)

3. **Importe o arquivo:**
   - Clique em **"Upload JSON file"**
   - Selecione o arquivo: `monitoring/grafana/dashboards/order-service-dashboard.json`
   - OU cole o conteúdo do arquivo JSON na área de texto

4. **Configure:**
   - **Name**: "Order Service - Resilience4j & Application Metrics" (ou deixe o padrão)
   - **Folder**: Selecione "Order Service" (ou crie uma nova)
   - **Prometheus**: Selecione "Prometheus" como datasource

5. **Clique em "Import"**

6. **Pronto!** O dashboard deve aparecer com todos os painéis.

## Método 2: Reiniciar Grafana (Provisioning Automático)

Se o provisioning não funcionou, tente:

1. **Pare o Grafana:**
   ```powershell
   wsl sudo docker stop grafana
   ```

2. **Remova o volume (opcional - apaga dados):**
   ```powershell
   wsl sudo docker volume rm order-service_grafana-data
   ```

3. **Inicie novamente:**
   ```powershell
   .\start-monitoring.ps1
   ```

4. **Aguarde 10-15 segundos** para o Grafana carregar

5. **Verifique:**
   - Vá em **Dashboards** → **Browse**
   - Deve aparecer na pasta "Order Service"

## Método 3: Verificar Provisioning

1. **Verifique os logs do Grafana:**
   ```powershell
   wsl sudo docker logs grafana | grep -i dashboard
   ```

2. **Verifique se o arquivo está montado:**
   ```powershell
   wsl sudo docker exec grafana ls -la /var/lib/grafana/dashboards/
   ```

   Deve mostrar `order-service-dashboard.json`

3. **Se não estiver, verifique o docker-compose.yml:**
   - O volume deve estar mapeado: `./monitoring/grafana/dashboards:/var/lib/grafana/dashboards`

## Se o Dashboard Aparecer Mas Estiver Vazio

### 1. Verifique o Datasource

1. Vá em **Configuration** → **Data Sources**
2. Clique em **Prometheus**
3. Clique em **"Save & Test"**
4. Deve mostrar: ✅ **"Data source is working"**

### 2. Gere Algumas Métricas

Faça algumas requisições à API:

```powershell
curl "http://localhost:8080/orders?orderNumber=ORDER-1"
curl "http://localhost:8080/orders?orderNumber=ORDER-2"
curl "http://localhost:8080/orders?orderNumber=ORDER-3"
```

Aguarde 10-15 segundos e atualize o dashboard (F5).

### 3. Verifique o Intervalo de Tempo

No canto superior direito do dashboard:
- Mude para **"Last 15 minutes"** ou **"Last 1 hour"**
- Clique em **Apply**

### 4. Teste uma Query Manualmente

1. Vá em **🔍 Explore**
2. Selecione **Prometheus**
3. Digite: `order_processed_total`
4. Clique em **Run query**
5. Se aparecer dados, o problema é no dashboard
6. Se não aparecer, o problema é no datasource ou métricas

## Troubleshooting

### Dashboard não aparece após import

- Verifique se o JSON é válido
- Verifique os logs: `wsl sudo docker logs grafana`
- Tente importar novamente

### "No data" em todos os painéis

- Verifique se há métricas: http://localhost:8080/actuator/prometheus
- Verifique o datasource
- Gere algumas requisições
- Ajuste o intervalo de tempo

### Erro ao importar

- Verifique se o arquivo JSON está completo
- Tente copiar e colar o conteúdo diretamente no Grafana
- Verifique a versão do Grafana (deve ser 8.0+)

## Dashboard Atualizado

O dashboard agora inclui:
- ✅ HTTP Request Rate
- ✅ HTTP Latency (p95)
- ✅ Circuit Breaker State
- ✅ Circuit Breaker Calls
- ✅ Retry Statistics
- ✅ Bulkhead Available Calls
- ✅ Rate Limiter Calls
- ✅ JVM Memory
- ✅ **Orders Processed Total** (NOVO)
- ✅ **Orders Failed by Reason** (NOVO)
- ✅ **Orders Successful Total** (NOVO)

Todos os painéis estão configurados para usar o datasource "Prometheus".

