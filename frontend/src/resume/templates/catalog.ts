export type TemplateCatalogItem={id:string;name:string;tags:string[];version:number};

export function mergeTemplateCatalog(previews:TemplateCatalogItem[],published:TemplateCatalogItem[]){
  const previewIds=new Set(previews.map(template=>template.id));
  const publishedById=new Map(published.map(template=>[template.id,template]));
  return {
    templates:[...previews.map(template=>publishedById.get(template.id)??template),...published.filter(template=>!previewIds.has(template.id))],
    unpublishedPreviewIds:new Set(previews.filter(template=>!publishedById.has(template.id)).map(template=>template.id)),
  };
}
