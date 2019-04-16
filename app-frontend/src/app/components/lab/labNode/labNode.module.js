import angular from 'angular';

import labNodeTpl from './labNode.html';
import LabActions from '_redux/actions/lab-actions';
import NodeActions from '_redux/actions/node-actions';
import { getNodeDefinition } from '_redux/node-utils';

class LabNodeController {
    constructor(
        $ngRedux,
        $scope,
        $log,
        $element,
        modalService,
        tokenService,
        projectService,
        APP_CONFIG,
        $rootScope
    ) {
        'ngInject';
        $rootScope.autoInject(this, arguments);

        this.tileServer = `${APP_CONFIG.tileServerLocation}`;
    }

    mapStateToThis(state) {
        return {
            readonly: state.lab.readonly,
            preventSelecting: state.lab.preventSelecting,
            analysis: state.lab.analysis,
            selectingNode: state.lab.selectingNode,
            selectedNode: state.lab.selectedNode,
            analysisErrors: state.lab.analysisErrors,
            node: getNodeDefinition(state, this)
        };
    }

    $onInit() {
        let unsubscribe = this.$ngRedux.connect(
            this.mapStateToThis.bind(this),
            Object.assign({}, LabActions, NodeActions)
        )(this);
        this.listeners = [
            this.$scope.$on('$destroy', unsubscribe),
            this.$scope.$watch('$ctrl.readonly', readonly => {
                if (readonly && !this.isCollapsed) {
                    this.toggleCollapse();
                }
            }),
            this.$scope.$watch('$ctrl.selectingNode', selectingNode => {
                if (selectingNode) {
                    this.$element.addClass('selectable-node');
                } else {
                    this.$element.removeClass('selectable-node');
                }
            }),
            this.$scope.$watch('$ctrl.selectedNode', selectedNode => {
                if (selectedNode === this.nodeId) {
                    this.$element.addClass('selected-node');
                } else {
                    this.$element.removeClass('selected-node');
                }
            })
        ];
        // Acceptable values are 'BODY', 'HISTOGRAM', and 'STATISTICS'
        this.currentView = 'BODY';
        this.isCollapsed = false;
        this.baseWidth = 400;
        this.histogramHeight = 250;
        this.statisticsHeight = 260;
        if (this.ifCellType(['const'])) {
            this.model.resize(this.baseWidth, 125);
        } else if (this.ifCellType(['classify'])) {
            this.model.resize(this.baseWidth, 275);
        }
    }

    $postLink() {
        this.$element.bind('click', this.onNodeClick.bind(this));
    }

    $onDestroy() {
        this.listeners.forEach(l => l());
    }

    preview() {
        if (!this.selectingNode) {
            this.selectNode(this.nodeId);
        }
    }

    toggleHistogram() {
        if (this.isCollapsed) {
            this.toggleCollapse();
        }
        if (this.currentView === 'BODY' && !this.bodyHeight) {
            this.bodyHeight = this.model.getBBox().height;
        }
        if (this.currentView === 'HISTOGRAM') {
            this.currentView = 'BODY';
            this.model.resize(this.baseWidth, this.bodyHeight);
        } else {
            this.currentView = 'HISTOGRAM';
            this.expandedSize = this.model.getBBox();
            this.model.resize(this.baseWidth, this.histogramHeight);
        }
    }

    toggleStatistics() {
        if (this.isCollapsed) {
            this.toggleCollapse();
        }
        if (this.currentView === 'BODY' && !this.bodyHeight) {
            this.bodyHeight = this.model.getBBox().height;
        }
        if (this.currentView === 'STATISTICS') {
            this.currentView = 'BODY';
            this.model.resize(this.baseWidth, this.bodyHeight);
        } else {
            this.currentView = 'STATISTICS';
            this.model.resize(this.baseWidth, this.statisticsHeight);
        }
    }

    toggleCollapse() {
        if (this.currentView === 'BODY' && !this.bodyHeight) {
            this.bodyHeight = this.model.getBBox().height;
        }
        if (this.isCollapsed) {
            this.model.resize(this.baseWidth, this.lastSize.height);
            this.isCollapsed = false;
        } else {
            this.lastSize = this.model.getBBox();
            this.model.resize(this.baseWidth, 50);
            this.isCollapsed = true;
        }
    }

    toggleBody() {
        this.showBody = !this.showBody;
        if (!this.showBody) {
            if (!this.showHistogram) {
                this.expandedSize = this.model.getBBox();
            }
            this.model.resize(this.expandedSize.width, 50);
        } else if (this.showHistogram) {
            this.model.resize(this.expandedSize.width, this.histogramHeight);
        } else {
            this.model.resize(this.expandedSize.width, this.expandedSize.height);
        }
    }

    ifCellType(typesArray) {
        if (!typesArray.length) {
            return false;
        }
        return typesArray.reduce((acc, type) => {
            return this.isSameCellType(type) || acc;
        }, this.isSameCellType(typesArray[0]));
    }

    isSameCellType(type) {
        return this.model.get('cellType') === type;
    }

    showCellBody() {
        return this.currentView === 'BODY' && !this.isCollapsed;
    }

    onNodeClick(event) {
        if (this.selectingNode && this.selectedNode !== this.nodeId && !this.preventSelecting) {
            event.stopPropagation();
            this.selectNode(this.nodeId);
        }
    }

    onNodeShare() {
        const nodeType = this.model.get('cellType');
        if (this.nodeId && this.analysis.id) {
            if (nodeType === 'projectSrc') {
                this.tokenService
                    .getOrCreateAnalysisMapToken({
                        organizationId: this.analysis.organizationId,
                        name: this.analysis.name + ' - ' + this.analysis.id,
                        project: this.node.projId
                    })
                    .then(mapToken => {
                        this.publishModal(
                            this.projectService.getProjectTileURL(this.node.projId, {
                                mapToken: mapToken.id
                            })
                        );
                    });
            } else {
                this.tokenService
                    .getOrCreateAnalysisMapToken({
                        organizationId: this.analysis.organizationId,
                        name: this.analysis.name + ' - ' + this.analysis.id,
                        toolRun: this.analysis.id
                    })
                    .then(mapToken => {
                        this.publishModal(
                            `${this.tileServer}/tools/${this.analysis.id}/{z}/{x}/{y}?mapToken=${
                                mapToken.id
                            }&node=${this.nodeId}`
                        );
                    });
            }
        }
    }

    publishModal(tileUrl) {
        if (tileUrl) {
            this.modalService
                .open({
                    component: 'rfProjectPublishModal',
                    resolve: {
                        tileUrl: () => tileUrl,
                        noDownload: () => true,
                        templateTitle: () => this.analysis.name
                    }
                })
                .result.catch(() => {});
        }
        return false;
    }
}

const LabNodeComponent = {
    templateUrl: labNodeTpl,
    controller: LabNodeController,
    bindings: {
        nodeId: '<',
        model: '<',
        enableSharing: '<'
    }
};

const LabNodeModule = angular.module('components.lab.labnode', []);
LabNodeModule.component('rfLabNode', LabNodeComponent);
export default LabNodeModule;
