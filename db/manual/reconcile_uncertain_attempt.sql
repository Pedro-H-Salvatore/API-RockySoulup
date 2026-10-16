-- Oracle/SQL*Plus; somente operador do banco, após comprovar cobrança real.
-- NÃO EXECUTAR automaticamente. Use binds; nunca preencher custo por palpite.
-- Defina :attempt_id, :confirmed_cost_micros, :actor_id, :reason.
-- Custo deve incluir as duas APIs efetivamente cobradas nesta tentativa.
DECLARE
  v_verification VARCHAR2(36);
  v_lock NUMBER;
BEGIN
  IF :confirmed_cost_micros IS NULL OR :confirmed_cost_micros < 0 OR :reason IS NULL THEN
    RAISE_APPLICATION_ERROR(-20001,'Custo confirmado e justificativa obrigatórios.');
  END IF;
  SELECT ID_CONTROL INTO v_lock FROM AI_CONTROL WHERE ID_CONTROL = 1 FOR UPDATE;
  SELECT ID_VERIFICACAO INTO v_verification FROM AI_ATTEMPT
    WHERE ID_ATTEMPT = :attempt_id AND ST_ATIVO = 0 AND ST_INCERTO = 1 FOR UPDATE;
  UPDATE AI_ATTEMPT SET NR_CUSTO = :confirmed_cost_micros, ST_INCERTO = 0
    WHERE ID_ATTEMPT = :attempt_id AND ST_ATIVO = 0 AND ST_INCERTO = 1;
  INSERT INTO VERIFICACAO_AUDIT
    (ID_AUDIT,ID_VERIFICACAO,ID_ATOR,DS_EVENTO,DS_JUSTIFICATIVA,DT_EVENTO)
    VALUES (LOWER(RAWTOHEX(SYS_GUID())),v_verification,:actor_id,'BILLING_RECONCILE',:reason,SYSTIMESTAMP);
END;
/
-- Conferir resultados antes de COMMIT; em caso de erro, ROLLBACK.
