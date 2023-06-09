/*
 * Copyright 2000-2023 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

using Mono.Cecil;

namespace TeamCity.Dotnet.Plugin.Agent.AssemblyLevelTestFilter.Infrastructure.DotnetAssembly;

internal class DotnetType : IDotnetType
{
    private readonly TypeDefinition _typeDefinition;
    
    public DotnetType(TypeDefinition typeDefinition)
    {
        _typeDefinition = typeDefinition;
    }

    public string FullName => _typeDefinition.FullName;
    
    public IEnumerable<CustomAttribute> CustomAttributes => _typeDefinition.CustomAttributes;
    
    public IEnumerable<MethodDefinition> Methods => _typeDefinition.Methods;
    
    public void RemoveCustomAttribute(CustomAttribute customAttribute)
    {
        _typeDefinition.CustomAttributes.Remove(customAttribute);
    }
}