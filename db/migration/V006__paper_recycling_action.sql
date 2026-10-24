-- Apenas dados: inclui a ação e liga à carta Reciclagem existente.
-- Pode ser executado novamente sem duplicar ou modificar registros anteriores.
MERGE INTO ACAO t USING (SELECT 'Reciclagem de papel' nome FROM DUAL) s
ON (UPPER(TRIM(t.NM_ACAO))=UPPER(TRIM(s.nome)))
WHEN NOT MATCHED THEN INSERT (NM_ACAO,NR_PONTOS,NR_VERSAO) VALUES(s.nome,20,0);

MERGE INTO ACAO_CARTA t USING (
 SELECT a.ID_ACAO,c.ID_CARTA FROM ACAO a CROSS JOIN CARTA c
 WHERE UPPER(TRIM(a.NM_ACAO))=UPPER('Reciclagem de papel')
 AND UPPER(TRIM(c.NM_CARTA))=UPPER('Reciclagem')
) s ON (t.ID_ACAO=s.ID_ACAO)
WHEN NOT MATCHED THEN INSERT (ID_ACAO,ID_CARTA,NR_CHANCE,QT_DUPLICADA)
VALUES(s.ID_ACAO,s.ID_CARTA,75,3);
