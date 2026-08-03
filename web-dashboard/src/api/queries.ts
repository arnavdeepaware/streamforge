import { useMutation, useQuery } from '@tanstack/react-query';
import { controlPlaneClient } from './controlPlaneClient';
import type {
  CreatePipelineRequest,
  PipelineConfiguration,
} from './controlPlaneClient';

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
