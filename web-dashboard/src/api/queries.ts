import { useQuery } from '@tanstack/react-query';
import { controlPlaneClient } from './controlPlaneClient';

export const controlPlaneQueryKeys = {
  pipelines: ['pipelines'] as const,
  pipeline: (pipelineId: string) => ['pipelines', pipelineId] as const,
  schemas: ['schemas'] as const,
};

export function usePipelines() {
  return useQuery({
    queryKey: controlPlaneQueryKeys.pipelines,
    queryFn: controlPlaneClient.listPipelines,
  });
}

export function usePipeline(pipelineId: string) {
  return useQuery({
    queryKey: controlPlaneQueryKeys.pipeline(pipelineId),
    queryFn: () => controlPlaneClient.getPipeline(pipelineId),
    enabled: pipelineId.length > 0,
  });
}

export function useSchemas() {
  return useQuery({
    queryKey: controlPlaneQueryKeys.schemas,
    queryFn: controlPlaneClient.listSchemas,
  });
}
