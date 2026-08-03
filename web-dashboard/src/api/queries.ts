import { useMutation, useQuery } from '@tanstack/react-query';
import { controlPlaneClient } from './controlPlaneClient';
import type {
  CreatePipelineRequest,
  PipelineConfiguration,
} from './controlPlaneClient';

export const controlPlaneQueryKeys = {
  pipelines: (page: number) => ['pipelines', { page }] as const,
  pipeline: (pipelineId: string) => ['pipelines', pipelineId] as const,
  latestRun: (pipelineId: string) =>
    ['pipelines', pipelineId, 'runs', 'latest'] as const,
  schemas: (page: number) => ['schemas', { page }] as const,
};

export function usePipelines(page = 0) {
  return useQuery({
    queryKey: controlPlaneQueryKeys.pipelines(page),
    queryFn: () => controlPlaneClient.listPipelines(page, 20),
  });
}

export function usePipeline(pipelineId: string) {
  return useQuery({
    queryKey: controlPlaneQueryKeys.pipeline(pipelineId),
    queryFn: () => controlPlaneClient.getPipeline(pipelineId),
    enabled: pipelineId.length > 0,
  });
}

export function useLatestPipelineRun(pipelineId: string) {
  return useQuery({
    queryKey: controlPlaneQueryKeys.latestRun(pipelineId),
    queryFn: () => controlPlaneClient.getLatestPipelineRun(pipelineId),
    enabled: pipelineId.length > 0,
  });
}

export function useSchemas(page = 0) {
  return useQuery({
    queryKey: controlPlaneQueryKeys.schemas(page),
    queryFn: () => controlPlaneClient.listSchemas(page, 20),
  });
}

export function useCanonicalFields() {
  return useQuery({
    queryKey: ['preview', 'canonical-fields'],
    queryFn: controlPlaneClient.canonicalFields,
    staleTime: Infinity,
  });
}

export function usePipelineValidation() {
  return useMutation({
    mutationFn: (configuration: PipelineConfiguration) =>
      controlPlaneClient.validatePipeline(configuration),
  });
}

export function usePipelineCreation() {
  return useMutation({
    mutationFn: (request: CreatePipelineRequest) =>
      controlPlaneClient.createPipeline(request),
  });
}
